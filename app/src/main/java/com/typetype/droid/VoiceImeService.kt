package com.typetype.droid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.typetype.droid.audio.AudioCaptureEngine
import com.typetype.droid.input.AndroidInputConnectionAdapter
import com.typetype.droid.input.InputCommitController
import com.typetype.droid.session.SessionEvent
import com.typetype.droid.session.VoiceSessionController
import com.typetype.droid.settings.VoiceImePreferences
import com.typetype.droid.translation.TranslationOutputMode
import com.typetype.droid.ui.VoiceInputView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VoiceImeService : InputMethodService() {
    private lateinit var inputView: VoiceInputView
    private lateinit var sessionController: VoiceSessionController
    private lateinit var preferences: VoiceImePreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "TypeTypeSession").apply { isDaemon = true }
    }
    private val translationExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "TypeTypeTranslation").apply { isDaemon = true }
    }

    override fun onCreate() {
        super.onCreate()
        TypeTypeReturnNotification.hide(this)
        FloatingImeSwitcherService.startIfAllowed(this)
        preferences = VoiceImePreferences(this)
        val app = application as TypeTypeApplication
        sessionController = VoiceSessionController(
            audioCaptureEngine = AudioCaptureEngine(),
            asrEngineFactory = app.asrEngineFactory,
            commitController = InputCommitController(),
            translationSettingsProvider = { preferences.loadTranslationSettings() },
            translationEngineResolver = { backend -> app.translationEngineFor(backend) },
            onStateChanged = { state -> inputViewOrNull()?.render(state) },
            backgroundExecutor = sessionExecutor,
            translationExecutor = translationExecutor,
            stateExecutor = { action -> mainHandler.post(action) },
        )
        val mode = preferences.loadEffectiveMode()
        sessionController.setMode(mode)
        app.warmUpAsr(mode)
        warmUpTranslationIfNeeded()
    }

    override fun onDestroy() {
        sessionExecutor.shutdownNow()
        translationExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        inputView = VoiceInputView(this).apply {
            onMicClicked = { toggleListening() }
            onDeleteClicked = { deleteBeforeCursor() }
            onDeleteAllClicked = { deleteAllText() }
            onSettingsClicked = { openSettings() }
            onSwitchInputMethodClicked = { showInputMethodPicker() }
        }
        inputView.render(sessionController.state)
        return inputView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val adapter = currentInputConnection?.let(::AndroidInputConnectionAdapter)
        sessionController.handle(SessionEvent.InputStarted(adapter))
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        FloatingImeSwitcherService.startIfAllowed(this)
        sessionController.setMode(preferences.loadEffectiveMode())
        warmUpTranslationIfNeeded()
        sessionController.handle(SessionEvent.PrepareRequested)
        inputViewOrNull()?.render(sessionController.state)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        sessionController.handle(SessionEvent.InputFinished)
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        sessionController.handle(SessionEvent.InputFinished)
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        sessionController.handle(
            SessionEvent.EditorSelectionChanged(
                oldSelectionStart = oldSelStart,
                newSelectionStart = newSelStart,
            ),
        )
    }

    private fun toggleListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            sessionController.handle(SessionEvent.Error("Microphone permission is missing"))
            return
        }

        if (sessionController.state.isActive) {
            sessionController.handle(SessionEvent.StopRequested)
        } else {
            val adapter = currentInputConnection?.let(::AndroidInputConnectionAdapter)
            sessionController.handle(SessionEvent.InputStarted(adapter))
            sessionController.handle(SessionEvent.StartRequested)
        }
    }

    private fun deleteBeforeCursor() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun deleteAllText() {
        val inputConnection = currentInputConnection ?: return
        val extractedText = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
        if (extractedText != null) {
            val text = extractedText.text ?: return
            val start = extractedText.startOffset
            val end = start + text.length
            inputConnection.beginBatchEdit()
            inputConnection.setSelection(start, end)
            inputConnection.commitText("", 1)
            inputConnection.endBatchEdit()
            return
        }
        inputConnection.deleteSurroundingText(Int.MAX_VALUE, Int.MAX_VALUE)
    }

    private fun openSettings() {
        if (sessionController.state.isActive) {
            sessionController.handle(SessionEvent.StopRequested)
        }
        requestHideSelf(0)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun showInputMethodPicker() {
        if (sessionController.state.isActive) {
            sessionController.handle(SessionEvent.StopRequested)
        }
        FloatingImeSwitcherService.startIfAllowed(this)
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }

    private fun warmUpTranslationIfNeeded() {
        val settings = preferences.loadTranslationSettings()
        if (settings.outputMode == TranslationOutputMode.TRANSLATION) {
            (application as TypeTypeApplication).warmUpTranslation(settings.backend, settings.targetLanguage)
        }
    }

    private fun inputViewOrNull(): VoiceInputView? = if (::inputView.isInitialized) inputView else null
}
