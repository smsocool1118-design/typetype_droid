package com.typetype.droid.session

import com.typetype.droid.asr.AsrEngine
import com.typetype.droid.asr.AsrEvent
import com.typetype.droid.asr.AsrEngineFactory
import com.typetype.droid.audio.AudioCaptureEngine
import com.typetype.droid.input.FakeEditableInputConnection
import com.typetype.droid.input.InputCommitController
import com.typetype.droid.translation.TranslationEngine
import com.typetype.droid.translation.TranslationOutputMode
import com.typetype.droid.translation.TranslationSettings
import com.typetype.droid.translation.TranslationTargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSessionControllerTest {
    @Test
    fun focusLossStopsSessionAndDropsConnection() {
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = FakeAsrEngineFactory(),
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(FakeEditableInputConnection()))
        controller.handle(SessionEvent.InputFinished)

        assertEquals(VoiceSessionState.Phase.IDLE, controller.state.phase)
    }

    @Test
    fun modeDoesNotChangeDuringActiveSession() {
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = FakeAsrEngineFactory(),
            commitController = InputCommitController(),
        )

        controller.setMode(DictationMode.OFFLINE)

        assertEquals(DictationMode.OFFLINE, controller.state.mode)
    }

    @Test
    fun externalClearKeepsSessionListeningAndDropsOldStreamingText() {
        val connection = FakeEditableInputConnection()
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.StreamingText("今天天气"))
        connection.clear()
        controller.handle(
            SessionEvent.EditorSelectionChanged(
                oldSelectionStart = 4,
                newSelectionStart = 0,
            ),
        )

        assertEquals("", connection.text)
        assertEquals(VoiceSessionState.Phase.LISTENING, controller.state.phase)
        assertEquals(1, asrEngineFactory.engine.resetCount)
    }

    @Test
    fun externalSelectionShorteningDropsOldTextEvenAfterStreamingSessionReset() {
        val connection = FakeEditableInputConnection()
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.StreamingText("今天天气"))
        controller.handle(SessionEvent.StreamingSegmentFinished)
        connection.clear()
        controller.handle(
            SessionEvent.EditorSelectionChanged(
                oldSelectionStart = 4,
                newSelectionStart = 0,
            ),
        )

        assertEquals("", connection.text)
        assertEquals(VoiceSessionState.Phase.LISTENING, controller.state.phase)
        assertEquals(1, asrEngineFactory.engine.resetCount)
    }

    @Test
    fun staleStreamingTextIsDroppedWhenFieldWasClearedWithoutSelectionEvent() {
        val connection = FakeEditableInputConnection()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = FakeAsrEngineFactory(),
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.handle(SessionEvent.StreamingText("今天天气"))
        controller.handle(SessionEvent.StreamingSegmentFinished)
        connection.clear()
        controller.handle(SessionEvent.StreamingText("今天天气不错"))

        assertEquals("", connection.text)
        assertEquals(VoiceSessionState.Phase.IDLE, controller.state.phase)
    }

    @Test
    fun freshSpeechAfterExternalClearContinuesWritingWithoutManualRestart() {
        val connection = FakeEditableInputConnection()
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.handle(SessionEvent.StreamingText("今天天气"))
        connection.clear()
        controller.handle(SessionEvent.StreamingText("今天天气不错"))
        controller.handle(SessionEvent.StreamingText("下一句"))

        assertEquals("下一句", connection.text)
        assertEquals(1, asrEngineFactory.engine.resetCount)
    }

    @Test
    fun offlineTranslationCommitsTranslatedText() {
        val connection = FakeEditableInputConnection()
        val translationEngine = FakeTranslationEngine("hello world")
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = FakeAsrEngineFactory(),
            commitController = InputCommitController(),
            translationSettingsProvider = {
                TranslationSettings(
                    outputMode = TranslationOutputMode.TRANSLATION,
                    targetLanguage = TranslationTargetLanguage.ENGLISH,
                )
            },
            translationEngineResolver = { translationEngine },
        )

        controller.setMode(DictationMode.OFFLINE)
        controller.handle(SessionEvent.InputStarted(connection))
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.OfflineText("今天天气不错"))

        assertEquals("hello world", connection.text)
        assertEquals("今天天气不错", translationEngine.lastInput)
        assertEquals(TranslationTargetLanguage.ENGLISH, translationEngine.lastLanguage)
        assertEquals(VoiceSessionState.Phase.LISTENING, controller.state.phase)
    }

    @Test
    fun stopInStreamingModeStructuresAlreadyWrittenText() {
        val connection = FakeEditableInputConnection()
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.StreamingText("今天有两件事第一修复流式第二测试粤语"))
        controller.handle(SessionEvent.StopRequested)

        assertEquals("今天有2件事：\n1. 修复流式。\n2. 测试粤语。", connection.text)
        assertEquals(VoiceSessionState.Phase.IDLE, controller.state.phase)
    }

    @Test
    fun streamingModeAddsQuestionMarkBeforeFollowingStatement() {
        val connection = FakeEditableInputConnection()
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.StreamingText("你今天有没有空，明天继续测试"))

        assertEquals("你今天有没有空？明天继续测试", connection.text)

        controller.handle(SessionEvent.StopRequested)

        assertEquals("你今天有没有空？明天继续测试。", connection.text)
        assertEquals(VoiceSessionState.Phase.IDLE, controller.state.phase)
    }

    @Test
    fun streamingRewriteButtonStructuresAndReplacesCurrentText() {
        val connection = FakeEditableInputConnection()
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.StreamingText("今天有两件事第一测试流式第二检查粤语"))
        assertEquals("今天有2件事第一测试流式第二检查粤语", controller.state.draftText)
        controller.handle(SessionEvent.StreamingRewriteRequested)

        assertEquals("今天有2件事：\n1. 测试流式。\n2. 检查粤语。", connection.text)
        assertEquals(VoiceSessionState.Phase.IDLE, controller.state.phase)
    }

    @Test
    fun streamingTextDropsUnknownArtifactsAndKeepsEnglish() {
        val connection = FakeEditableInputConnection()
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.StreamingText("有<unk> enough，<unk>行了"))

        assertEquals("有 enough，行了", connection.text)
        assertEquals("有 enough，行了", controller.state.draftText)
    }

    @Test
    fun longStreamingDraftIsKeptCompleteForScrollablePanel() {
        val connection = FakeEditableInputConnection()
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )
        val longText = "这个地方先讲研究方法再讲结论然后讲风险最后讲下一步安排".repeat(4)

        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.StreamingText(longText))

        assertEquals(longText, controller.state.draftText)
        assertEquals(longText, connection.text)
    }

    @Test
    fun streamingRewriteButtonUsesEditorTextWhenStreamingCacheIsEmpty() {
        val connection = FakeEditableInputConnection("今天有两件事第一测试流式第二检查粤语")
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.StreamingRewriteRequested)

        assertEquals("今天有2件事：\n1. 测试流式。\n2. 检查粤语。", connection.text)
        assertEquals(VoiceSessionState.Phase.IDLE, controller.state.phase)
    }

    @Test
    fun streamingRewritePrefersRefreshedEditorTextOverStaleStreamingCache() {
        val oldConnection = FakeEditableInputConnection()
        val newConnection = FakeEditableInputConnection("今天有两件事第一整理微信输入框第二测试AI带入")
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.handle(SessionEvent.InputStarted(oldConnection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.StreamingText("旧的流式缓存"))
        controller.refreshInputConnection(newConnection)
        controller.handle(SessionEvent.StreamingRewriteRequested)

        assertEquals("旧的流式缓存", oldConnection.text)
        assertEquals("今天有2件事：\n1. 整理微信输入框。\n2. 测试AI带入。", newConnection.text)
        assertEquals(VoiceSessionState.Phase.IDLE, controller.state.phase)
    }

    @Test
    fun offlineModeCommitsStructuredWholeUtterance() {
        val connection = FakeEditableInputConnection()
        val asrEngineFactory = FakeAsrEngineFactory()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = asrEngineFactory,
            commitController = InputCommitController(),
        )

        controller.setMode(DictationMode.OFFLINE)
        controller.handle(SessionEvent.InputStarted(connection))
        controller.installEngineForTest(asrEngineFactory.engine)
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.OfflineText("今天有两件事第一修复流式第二测试粤语"))
        controller.handle(SessionEvent.StopRequested)

        assertEquals("今天有2件事：\n1. 修复流式。\n2. 测试粤语。", connection.text)
        assertEquals(VoiceSessionState.Phase.IDLE, controller.state.phase)
    }

    @Test
    fun translationOutputFailsInStreamingMode() {
        val connection = FakeEditableInputConnection()
        val controller = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = FakeAsrEngineFactory(),
            commitController = InputCommitController(),
            translationSettingsProvider = {
                TranslationSettings(
                    outputMode = TranslationOutputMode.TRANSLATION,
                    targetLanguage = TranslationTargetLanguage.ENGLISH,
                )
            },
            translationEngineResolver = { FakeTranslationEngine("ignored") },
        )

        controller.handle(SessionEvent.InputStarted(connection))
        controller.setPhaseForTest(VoiceSessionState.Phase.LISTENING)
        controller.handle(SessionEvent.OfflineText("今天天气不错"))

        assertEquals("", connection.text)
        assertEquals("翻译输出仅支持稳妥模式", controller.state.error)
        assertEquals(VoiceSessionState.Phase.ERROR, controller.state.phase)
    }
}

private class FakeAsrEngineFactory(
    private val finalEvents: List<AsrEvent> = emptyList(),
) : AsrEngineFactory {
    val engine = FakeAsrEngine(finalEvents)

    override fun create(mode: DictationMode, onEvent: (AsrEvent) -> Unit): AsrEngine {
        engine.onEvent = onEvent
        return engine
    }
}

private class FakeAsrEngine(
    private val finalEvents: List<AsrEvent>,
) : AsrEngine {
    var resetCount = 0
        private set
    var onEvent: ((AsrEvent) -> Unit)? = null

    override fun acceptSamples(samples: FloatArray) = Unit

    override fun finish() {
        finalEvents.forEach { onEvent?.invoke(it) }
    }

    override fun reset() {
        resetCount += 1
    }

    override fun close() = Unit
}

private class FakeTranslationEngine(
    private val output: String,
) : TranslationEngine {
    var lastInput: String? = null
        private set
    var lastLanguage: TranslationTargetLanguage? = null
        private set

    override fun warmUp(targetLanguage: TranslationTargetLanguage) = Unit

    override fun translate(text: String, targetLanguage: TranslationTargetLanguage): String {
        lastInput = text
        lastLanguage = targetLanguage
        return output
    }

    override fun close() = Unit
}

private fun VoiceSessionController.installEngineForTest(engine: AsrEngine) {
    val field = VoiceSessionController::class.java.getDeclaredField("engine")
    field.isAccessible = true
    field.set(this, engine)
}

private fun VoiceSessionController.setPhaseForTest(phase: VoiceSessionState.Phase) {
    val field = VoiceSessionController::class.java.getDeclaredField("state")
    field.isAccessible = true
    field.set(this, state.copy(phase = phase))
}
