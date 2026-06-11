package com.typetype.droid

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

class ImePickerActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var pickerShown = false
    private var finishedAfterSelection = false
    private var initialInputMethod: String? = null
    private var startedAtMillis = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialInputMethod = currentInputMethod()
        startedAtMillis = System.currentTimeMillis()
        window.decorView.postDelayed({ showPicker() }, SHOW_PICKER_DELAY_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.post { showPicker() }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun showPicker() {
        if (pickerShown) return
        pickerShown = true
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        handler.postDelayed(::watchForSelection, POLL_INTERVAL_MS)
        handler.postDelayed({ finishIfStillWaiting() }, PICKER_TIMEOUT_MS)
    }

    private fun watchForSelection() {
        if (isFinishing || finishedAfterSelection) return
        val current = currentInputMethod()
        if (current != null && current != initialInputMethod) {
            finishedAfterSelection = true
            handler.postDelayed({ finish() }, FINISH_AFTER_SELECTION_MS)
            return
        }
        if (System.currentTimeMillis() - startedAtMillis < PICKER_TIMEOUT_MS) {
            handler.postDelayed(::watchForSelection, POLL_INTERVAL_MS)
        } else {
            finishIfStillWaiting()
        }
    }

    private fun finishIfStillWaiting() {
        if (!isFinishing && !finishedAfterSelection) {
            finish()
        }
    }

    private fun currentInputMethod(): String? {
        return Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    }

    private companion object {
        const val SHOW_PICKER_DELAY_MS = 40L
        const val POLL_INTERVAL_MS = 160L
        const val FINISH_AFTER_SELECTION_MS = 350L
        const val PICKER_TIMEOUT_MS = 12_000L
    }
}
