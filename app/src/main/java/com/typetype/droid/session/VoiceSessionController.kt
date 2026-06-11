package com.typetype.droid.session

import android.annotation.SuppressLint
import android.os.SystemClock
import android.util.Log
import com.typetype.droid.asr.AsrEvent
import com.typetype.droid.asr.AsrEngine
import com.typetype.droid.asr.AsrEngineFactory
import com.typetype.droid.audio.AudioCaptureEngine
import com.typetype.droid.dictionary.DictionaryStore
import com.typetype.droid.input.EditableInputConnection
import com.typetype.droid.input.InputCommitController
import com.typetype.droid.input.CircularAudioBuffer
import com.typetype.droid.rewrite.LlmRewriteEngine
import com.typetype.droid.rewrite.RuleBasedTextRewriteEngine
import com.typetype.droid.rewrite.StructuredTextFormatter
import com.typetype.droid.rewrite.TextRewriteEngine
import com.typetype.droid.settings.Android031Settings
import com.typetype.droid.settings.RewriteBackendPreference
import com.typetype.droid.settings.StreamingEnhancementMode
import com.typetype.droid.translation.TranslationEngine
import com.typetype.droid.translation.TranslationEngineResolver
import com.typetype.droid.translation.TranslationOutputMode
import com.typetype.droid.translation.TranslationSettings
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

class VoiceSessionController(
    private val audioCaptureEngine: AudioCaptureEngine,
    private val asrEngineFactory: AsrEngineFactory,
    private val commitController: InputCommitController,
    private val translationSettingsProvider: () -> TranslationSettings = { TranslationSettings() },
    private val android031SettingsProvider: () -> Android031Settings = { Android031Settings() },
    private val translationEngineResolver: TranslationEngineResolver? = null,
    private val dictionaryStore: DictionaryStore? = null,
    private val llmRewriteEngine: LlmRewriteEngine? = null,
    private val textRewriteEngine: TextRewriteEngine = RuleBasedTextRewriteEngine(),
    private val onStateChanged: (VoiceSessionState) -> Unit = {},
    private val backgroundExecutor: Executor = Executor { it.run() },
    private val translationExecutor: Executor = backgroundExecutor,
    private val stateExecutor: Executor = Executor { it.run() },
) {
    var state: VoiceSessionState = VoiceSessionState()
        private set

    private var engine: AsrEngine? = null
    private var connection: EditableInputConnection? = null
    private var preparedMode: DictationMode? = null
    private var preparingMode: DictationMode? = null
    private var asrEventGeneration = 0
    private var streamingCommittedText = ""
    private var streamingActiveText = ""
    private var streamingSegmentPrefix = ""
    private var stopCompletionPending = false

    fun setMode(mode: DictationMode) {
        if (state.isActive || state.phase == VoiceSessionState.Phase.PREPARING) return
        update(state.copy(mode = mode, error = null))
    }

    fun refreshInputConnection(nextConnection: EditableInputConnection?) {
        connection = nextConnection
        commitController.updateConnection(nextConnection)
    }

    fun handle(event: SessionEvent) {
        when (event) {
            is SessionEvent.InputStarted -> {
                connection = event.connection
                commitController.attach(event.connection)
                if (event.connection != null && state.draftText.isNotEmpty()) {
                    update(state.copy(error = null, draftText = ""))
                }
                if (event.connection == null) {
                    stopSession(finalizeRecognition = false)
                }
            }

            SessionEvent.InputFinished -> stopSession(finalizeRecognition = false)
            SessionEvent.PrepareRequested -> prepareSession()
            SessionEvent.StartRequested -> startSession()
            SessionEvent.StopRequested -> stopSession(finalizeRecognition = true)
            is SessionEvent.EditorSelectionChanged -> handleEditorSelectionChanged(event)
            is SessionEvent.StreamingText -> writeStreamingText(event.text)
            SessionEvent.StreamingSegmentFinished -> finishStreamingSegment()
            SessionEvent.StreamingRewriteRequested -> rewriteCurrentStreamingText()
            is SessionEvent.OfflineText -> writeOfflineText(event.text)
            is SessionEvent.Error -> fail(event.message)
        }
    }

    private fun writeStreamingText(text: String) {
        if (shouldTreatAsExternalCommitBoundary()) {
            resetCurrentDictationSegment()
            return
        }
        val cleanedText = StructuredTextFormatter.removeAsrArtifacts(text)
        if (cleanedText.isBlank()) return
        val outputText = StructuredTextFormatter.punctuateStreamingQuestions(streamingOutputText(cleanedText))
        streamingActiveText = outputText
        commitController.writeStreaming(outputText)
        update(state.copy(error = null, draftText = currentStreamingTranscript()))
    }

    private fun writeOfflineText(text: String) {
        if (shouldTreatAsExternalCommitBoundary()) {
            resetCurrentDictationSegment()
            return
        }
        val cleanedText = StructuredTextFormatter.removeAsrArtifacts(text)
        if (cleanedText.isBlank()) return
        val android031Settings = android031SettingsProvider()
        val normalizedInput = dictionaryStore?.applyReplacements(cleanedText) ?: cleanedText
        val translationSettings = translationSettingsProvider()
        if (translationSettings.outputMode == TranslationOutputMode.TRANSLATION) {
            if (state.mode != DictationMode.OFFLINE) {
                fail("翻译输出仅支持稳妥模式")
                return
            }
            translateOfflineText(normalizedInput, translationSettings)
            return
        }
        commitDictationText(normalizedInput, android031Settings)
    }

    private fun commitDictationText(
        text: String,
        settings: Android031Settings,
    ) {
        val localText = if (settings.voiceFormattingEnabled) {
            textRewriteEngine.rewrite(text)
        } else {
            StructuredTextFormatter.ensureFinalPunctuation(text)
        }
        dictionaryStore?.autoLearnFromText(localText, settings.autoLearningEnabled)
        val llm = llmRewriteEngine
        if (settings.rewriteBackend != RewriteBackendPreference.AI || llm == null || !llm.isConfigured(settings)) {
            commitController.commitFinal(localText)
            return
        }

        val currentGeneration = asrEventGeneration
        val preserveTerms = dictionaryStore?.preserveTermsFor(localText, settings) ?: emptyList()
        update(state.copy(phase = VoiceSessionState.Phase.TRANSLATING, error = null))
        backgroundExecutor.execute {
            val rewritten = runCatching {
                llm.rewrite(localText, settings, preserveTerms)
            }.getOrElse { error ->
                logError("LLM rewrite failed; falling back to local rewrite", error)
                localText
            }
            stateExecutor.execute applyRewrite@{
                if (currentGeneration != asrEventGeneration) return@applyRewrite
                if (shouldTreatAsExternalCommitBoundary()) {
                    resetCurrentDictationSegment()
                    return@applyRewrite
                }
                val finalText = StructuredTextFormatter.normalizeNumbers(
                    StructuredTextFormatter.removeAsrArtifacts(rewritten).trim(),
                ).ifBlank { localText }
                commitController.commitFinal(finalText)
                if (stopCompletionPending) {
                    completeFinalizedStop()
                } else if (state.phase == VoiceSessionState.Phase.TRANSLATING) {
                    update(state.copy(phase = VoiceSessionState.Phase.LISTENING, error = null))
                }
            }
        }
    }

    private fun rewriteCurrentStreamingText() {
        val ownedText = StructuredTextFormatter.removeAsrArtifacts(
            currentStreamingTranscript()
                .ifBlank { commitController.currentStreamingText() }
                .ifBlank { state.draftText },
        )
        val editorText = StructuredTextFormatter.removeAsrArtifacts(
            commitController.textBeforeCursor(MAX_EDITOR_REWRITE_FALLBACK_CHARS),
        )
        val rawText = editorText.ifBlank { ownedText }
        if (rawText.isBlank()) {
            update(state.copy(error = "当前没有可整理的流式文字"))
            return
        }
        val replaceSource = editorText.takeIf { it.isNotBlank() }

        val settings = android031SettingsProvider()
        val normalizedInput = dictionaryStore?.applyReplacements(rawText) ?: rawText
        val localText = if (settings.voiceFormattingEnabled) {
            textRewriteEngine.rewrite(normalizedInput)
        } else {
            StructuredTextFormatter.ensureFinalPunctuation(normalizedInput)
        }
        dictionaryStore?.autoLearnFromText(localText, settings.autoLearningEnabled)
        update(state.copy(error = null, draftText = localText))

        val useAi = settings.streamingEnhancementMode == StreamingEnhancementMode.ONLINE_ENHANCED
        val llm = llmRewriteEngine
        if (!useAi || llm == null || !llm.isConfigured(settings)) {
            finalizeStreamingRewrite(localText, replaceSource)
            return
        }

        val currentGeneration = asrEventGeneration
        val preserveTerms = dictionaryStore?.preserveTermsFor(localText, settings) ?: emptyList()
        val engineToClose = engine
        engine = null
        preparedMode = null
        preparingMode = null
        update(state.copy(phase = VoiceSessionState.Phase.TRANSLATING, error = null))
        backgroundExecutor.execute {
            audioCaptureEngine.stop()
            engineToClose?.close()
            val rewritten = runCatching {
                llm.rewrite(localText, settings.copy(rewriteBackend = RewriteBackendPreference.AI), preserveTerms)
            }.getOrElse { error ->
                logError("streaming AI rewrite failed; falling back to local rewrite", error)
                localText
            }
            stateExecutor.execute applyRewrite@{
                if (currentGeneration != asrEventGeneration) return@applyRewrite
                update(state.copy(draftText = rewritten))
                finalizeStreamingRewrite(rewritten, replaceSource)
            }
        }
    }

    private fun finalizeStreamingRewrite(text: String, replaceSource: String? = null) {
        val engineToClose = engine
        val rewritten = StructuredTextFormatter.normalizeNumbers(
            StructuredTextFormatter.removeAsrArtifacts(text).trim(),
        )
        if (rewritten.isNotBlank()) {
            val replaced = if (replaceSource.isNullOrBlank()) {
                commitController.replaceStreamingText(rewritten)
            } else {
                commitController.replaceTextBeforeCursor(replaceSource, rewritten)
            }
            if (!replaced) {
                update(state.copy(
                    phase = VoiceSessionState.Phase.ERROR,
                    error = "当前输入框暂时不可替换，请重新点输入框后再带入",
                    draftText = rewritten,
                ))
                backgroundExecutor.execute {
                    audioCaptureEngine.stop()
                    engineToClose?.close()
                }
                return
            }
        }
        stopCompletionPending = false
        asrEventGeneration += 1
        commitController.resetSession()
        commitController.detach()
        connection = null
        engine = null
        preparedMode = null
        preparingMode = null
        clearStreamingTranscript()
        update(state.copy(phase = VoiceSessionState.Phase.IDLE, error = null))
        backgroundExecutor.execute {
            audioCaptureEngine.stop()
            engineToClose?.close()
        }
    }

    private fun streamingOutputText(text: String): String {
        if (streamingActiveText.isEmpty()) {
            val prefixed = StructuredTextFormatter.prefixStreamingBoundaryPunctuation(streamingCommittedText, text)
            streamingSegmentPrefix = if (text.isNotEmpty() && prefixed.endsWith(text)) {
                prefixed.dropLast(text.length)
            } else {
                ""
            }
            return prefixed
        }
        return streamingSegmentPrefix + text
    }

    private fun finishStreamingSegment() {
        if (streamingActiveText.isNotBlank()) {
            streamingCommittedText += streamingActiveText
        }
        streamingActiveText = ""
        streamingSegmentPrefix = ""
        commitController.finishStreamingSegment()
        update(state.copy(error = null, draftText = currentStreamingTranscript()))
    }

    private fun translateOfflineText(
        text: String,
        translationSettings: TranslationSettings,
    ) {
        val targetLanguage = translationSettings.targetLanguage
        val translationEngine = translationEngineResolver?.resolve(translationSettings.backend)
        val currentGeneration = asrEventGeneration
        logInfo("translateOfflineText backend=${translationSettings.backend} target=$targetLanguage chars=${text.length}")
        update(state.copy(phase = VoiceSessionState.Phase.TRANSLATING, error = null))
        translationExecutor.execute translateWork@{
            val startMs = elapsedRealtimeOrZero()
            val translated = try {
                translationEngine?.translate(text, targetLanguage)
                    ?: error("Translation engine is unavailable")
            } catch (error: Throwable) {
                logError("translateOfflineText failed", error)
                stateExecutor.execute {
                    if (currentGeneration == asrEventGeneration) {
                        fail(error.message ?: "翻译失败")
                    }
                }
                return@translateWork
            }
            stateExecutor.execute applyTranslation@{
                if (currentGeneration != asrEventGeneration) return@applyTranslation
                if (translated.isBlank()) {
                    fail("本地翻译没有返回 ${targetLanguage.label} 文本")
                    return@applyTranslation
                }
                if (shouldTreatAsExternalCommitBoundary()) {
                    resetCurrentDictationSegment()
                    return@applyTranslation
                }
                logInfo("translateOfflineText done elapsed=${elapsedRealtimeOrZero() - startMs}ms chars=${translated.length}")
                commitController.commitFinal(translated)
                if (stopCompletionPending) {
                    completeFinalizedStop()
                } else if (state.phase == VoiceSessionState.Phase.TRANSLATING) {
                    update(state.copy(phase = VoiceSessionState.Phase.LISTENING, error = null))
                }
            }
        }
    }

    private fun shouldTreatAsExternalCommitBoundary(): Boolean {
        return commitController.hasWrittenOutput() && commitController.cursorIsAtStart()
    }

    private fun resetCurrentDictationSegment() {
        asrEventGeneration += 1
        commitController.resetAfterExternalCommit()
        clearStreamingTranscript()
        engine?.reset()
    }

    private fun handleEditorSelectionChanged(event: SessionEvent.EditorSelectionChanged) {
        if (!state.isActive) return
        if (!commitController.hasWrittenOutput()) return
        val selectionMovedBackward = event.oldSelectionStart > 0 && event.newSelectionStart < event.oldSelectionStart
        val fieldLikelyClearedAfterSend = event.newSelectionStart == 0
        if (fieldLikelyClearedAfterSend || selectionMovedBackward || commitController.hasExternalChangeToStreamingText()) {
            resetCurrentDictationSegment()
        }
    }

    private fun prepareSession() {
        if (connection == null || engine != null || state.phase == VoiceSessionState.Phase.PREPARING) return
        val requestedMode = state.mode
        preparingMode = requestedMode
        update(state.copy(phase = VoiceSessionState.Phase.PREPARING, error = null))

        backgroundExecutor.execute prepareWork@{
            val startMs = SystemClock.elapsedRealtime()
            val nextEngine = try {
                asrEngineFactory.create(requestedMode, asrEventHandler(requestedMode))
            } catch (error: Throwable) {
                stateExecutor.execute {
                    if (preparingMode == requestedMode) {
                        preparingMode = null
                        fail(error.message ?: "Unable to initialize ASR engine")
                    }
                }
                return@prepareWork
            }
            val engineReadyMs = SystemClock.elapsedRealtime()
            Log.d(TAG, "prepareSession mode=$requestedMode engine=${engineReadyMs - startMs}ms")

            stateExecutor.execute {
                val canKeepPreparedEngine = state.phase == VoiceSessionState.Phase.PREPARING ||
                    state.phase == VoiceSessionState.Phase.STARTING ||
                    state.phase == VoiceSessionState.Phase.LISTENING
                if (connection == null || state.mode != requestedMode || !canKeepPreparedEngine) {
                    nextEngine.close()
                    if (preparingMode == requestedMode) preparingMode = null
                    return@execute
                }
                engine = nextEngine
                preparedMode = requestedMode
                preparingMode = null
                if (state.phase == VoiceSessionState.Phase.PREPARING) {
                    update(state.copy(phase = VoiceSessionState.Phase.READY, error = null))
                }
            }
        }
    }

    private fun startSession() {
        if (state.isActive || connection == null) return
        update(state.copy(phase = VoiceSessionState.Phase.STARTING, error = null))

        val requestedMode = state.mode
        backgroundExecutor.execute startWork@{
            val startMs = SystemClock.elapsedRealtime()
            val pendingSamples = CircularAudioBuffer(MAX_PENDING_AUDIO_CHUNKS)
            val liveEngine = AtomicReference<AsrEngine?>()
            engine?.takeIf { preparedMode == requestedMode }?.let(liveEngine::set)

            @SuppressLint("MissingPermission")
            val started = audioCaptureEngine.start { samples ->
                val targetEngine = liveEngine.get()
                if (targetEngine != null) {
                    targetEngine.acceptSamples(samples)
                } else {
                    pendingSamples.add(samples)
                }
            }
            val audioReadyMs = SystemClock.elapsedRealtime()

            stateExecutor.execute applyAudioStart@{
                if (state.phase != VoiceSessionState.Phase.STARTING) {
                    audioCaptureEngine.stop()
                    return@applyAudioStart
                }

                if (started) {
                    update(state.copy(phase = VoiceSessionState.Phase.LISTENING))
                } else {
                    fail("Unable to start microphone capture")
                }
            }
            if (!started) {
                Log.d(TAG, "startSession mode=$requestedMode audio=${audioReadyMs - startMs}ms started=false")
                return@startWork
            }

            val preparedEngine = liveEngine.get()
            val nextEngine = preparedEngine ?: try {
                asrEngineFactory.create(requestedMode, asrEventHandler(requestedMode))
            } catch (error: Throwable) {
                stateExecutor.execute {
                    if (state.phase == VoiceSessionState.Phase.STARTING || state.phase == VoiceSessionState.Phase.LISTENING) {
                        fail(error.message ?: "Unable to initialize ASR engine")
                    }
                }
                return@startWork
            }
            val engineReadyMs = SystemClock.elapsedRealtime()
            liveEngine.set(nextEngine)
            drainPendingSamples(pendingSamples, nextEngine)
            Log.d(
                TAG,
                "startSession mode=$requestedMode audio=${audioReadyMs - startMs}ms engine=${engineReadyMs - audioReadyMs}ms",
            )

            stateExecutor.execute applyEngine@{
                if (!state.isActive) {
                    if (preparedEngine == null) {
                        nextEngine.close()
                    }
                    return@applyEngine
                }
                engine = nextEngine
                preparedMode = requestedMode
            }
        }
    }

    private fun stopSession(finalizeRecognition: Boolean) {
        if (!state.isActive && state.phase != VoiceSessionState.Phase.ERROR) {
            val engineToClose = engine
            engine = null
            preparedMode = null
            asrEventGeneration += 1
            commitController.detach()
            connection = null
            clearStreamingTranscript()
            stopCompletionPending = false
            if (state.phase != VoiceSessionState.Phase.IDLE) {
                update(state.copy(phase = VoiceSessionState.Phase.IDLE, error = null, draftText = ""))
            }
            backgroundExecutor.execute {
                engineToClose?.close()
            }
            return
        }
        if (state.phase == VoiceSessionState.Phase.STOPPING) return
        val stopPhase = if (finalizeRecognition && state.mode == DictationMode.OFFLINE) {
            VoiceSessionState.Phase.DECODING
        } else {
            VoiceSessionState.Phase.STOPPING
        }
        update(state.copy(phase = stopPhase, error = null))
        val engineToClose = engine
        engine = null
        preparedMode = null
        preparingMode = null
        stopCompletionPending = finalizeRecognition
        if (!finalizeRecognition) {
            asrEventGeneration += 1
            commitController.resetSession()
            commitController.detach()
            connection = null
            clearStreamingTranscript()
        }
        backgroundExecutor.execute {
            audioCaptureEngine.stop()
            val finishError = if (finalizeRecognition) {
                runCatching { engineToClose?.finish() }.exceptionOrNull()
            } else {
                null
            }
            engineToClose?.close()
            stateExecutor.execute stopApply@{
                if (finishError != null) {
                    fail(finishError.message ?: "Unable to finalize ASR result")
                    return@stopApply
                }
                if (!finalizeRecognition) {
                    if (state.phase == VoiceSessionState.Phase.STOPPING) {
                        update(state.copy(phase = VoiceSessionState.Phase.IDLE, error = null))
                    }
                    return@stopApply
                }
                if (stopCompletionPending && state.phase != VoiceSessionState.Phase.TRANSLATING) {
                    completeFinalizedStop()
                }
            }
        }
    }

    private fun completeFinalizedStop() {
        if (state.mode == DictationMode.STREAMING) {
            finalizeStreamingOutput()
        }
        stopCompletionPending = false
        asrEventGeneration += 1
        commitController.resetSession()
        commitController.detach()
        connection = null
        clearStreamingTranscript()
        update(state.copy(phase = VoiceSessionState.Phase.IDLE, error = null, draftText = ""))
    }

    private fun finalizeStreamingOutput() {
        val transcript = StructuredTextFormatter.removeAsrArtifacts(currentStreamingTranscript())
        if (transcript.isBlank()) return
        val rewritten = textRewriteEngine.rewrite(transcript)
        if (rewritten.isNotBlank()) {
            commitController.replaceStreamingText(rewritten)
        }
    }

    private fun currentStreamingTranscript(): String {
        return streamingCommittedText + streamingActiveText
    }

    private fun clearStreamingTranscript() {
        streamingCommittedText = ""
        streamingActiveText = ""
        streamingSegmentPrefix = ""
    }

    private fun fail(message: String) {
        val engineToClose = engine
        engine = null
        preparedMode = null
        preparingMode = null
        stopCompletionPending = false
        asrEventGeneration += 1
        commitController.resetSession()
        clearStreamingTranscript()
        backgroundExecutor.execute {
            audioCaptureEngine.stop()
            engineToClose?.close()
        }
        update(state.copy(phase = VoiceSessionState.Phase.ERROR, error = message))
    }

    private fun update(next: VoiceSessionState) {
        state = next
        onStateChanged(next)
    }

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun logError(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }

    private fun elapsedRealtimeOrZero(): Long {
        return runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)
    }

    private fun drainPendingSamples(
        pendingSamples: CircularAudioBuffer,
        targetEngine: AsrEngine,
    ) {
        pendingSamples.drain { samples ->
            targetEngine.acceptSamples(samples)
        }
    }

    private fun asrEventHandler(mode: DictationMode): (AsrEvent) -> Unit {
        return { event ->
            val eventGeneration = asrEventGeneration
            stateExecutor.execute {
                if (eventGeneration == asrEventGeneration) {
                    handle(event.toSessionEvent(mode))
                }
            }
        }
    }

    private fun AsrEvent.toSessionEvent(mode: DictationMode): SessionEvent {
        return when (this) {
            is AsrEvent.Text -> {
                if (mode == DictationMode.STREAMING) {
                    SessionEvent.StreamingText(value)
                } else {
                    SessionEvent.OfflineText(value)
                }
            }

            AsrEvent.SegmentFinished -> {
                if (mode == DictationMode.STREAMING) {
                    SessionEvent.StreamingSegmentFinished
                } else {
                    SessionEvent.OfflineText("")
                }
            }
        }
    }

    private companion object {
        const val TAG = "VoiceSessionController"
        const val MAX_PENDING_AUDIO_CHUNKS = 160
        const val MAX_EDITOR_REWRITE_FALLBACK_CHARS = 2000
    }
}
