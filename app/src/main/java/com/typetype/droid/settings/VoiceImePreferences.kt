package com.typetype.droid.settings

import android.content.Context
import com.typetype.droid.session.DictationMode
import com.typetype.droid.translation.TranslationBackend
import com.typetype.droid.translation.TranslationOutputMode
import com.typetype.droid.translation.TranslationSettings
import com.typetype.droid.translation.TranslationTargetLanguage

class VoiceImePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("voice_ime_preferences", Context.MODE_PRIVATE)

    fun loadMode(): DictationMode {
        return runCatching {
            DictationMode.valueOf(preferences.getString(KEY_MODE, DictationMode.STREAMING.name)!!)
        }.getOrDefault(DictationMode.STREAMING)
    }

    fun loadEffectiveMode(): DictationMode {
        return if (loadTranslationSettings().outputMode == TranslationOutputMode.TRANSLATION) {
            DictationMode.OFFLINE
        } else {
            loadMode()
        }
    }

    fun saveMode(mode: DictationMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun loadTranslationSettings(): TranslationSettings {
        return normalizeTranslationSettings(
            TranslationSettings(
            outputMode = runCatching {
                TranslationOutputMode.valueOf(
                    preferences.getString(KEY_OUTPUT_MODE, TranslationOutputMode.DICTATION.name)!!,
                )
            }.getOrDefault(TranslationOutputMode.DICTATION),
            backend = runCatching {
                TranslationBackend.valueOf(
                    preferences.getString(KEY_BACKEND, TranslationBackend.ML_KIT.name)!!,
                )
            }.getOrDefault(TranslationBackend.ML_KIT),
            targetLanguage = runCatching {
                TranslationTargetLanguage.valueOf(
                    preferences.getString(KEY_TARGET_LANGUAGE, TranslationTargetLanguage.ENGLISH.name)!!,
                )
            }.getOrDefault(TranslationTargetLanguage.ENGLISH),
            ),
        )
    }

    fun saveTranslationOutputMode(mode: TranslationOutputMode) {
        preferences.edit().putString(KEY_OUTPUT_MODE, mode.name).apply()
    }

    fun saveTranslationBackend(backend: TranslationBackend) {
        preferences.edit().putString(KEY_BACKEND, backend.name).apply()
    }

    fun saveTranslationTargetLanguage(targetLanguage: TranslationTargetLanguage) {
        val editor = preferences.edit().putString(KEY_TARGET_LANGUAGE, targetLanguage.name)
        if (!targetLanguage.isMlKitSupported) {
            editor.putString(KEY_BACKEND, TranslationBackend.HY_MT.name)
        }
        editor.apply()
    }

    private fun normalizeTranslationSettings(settings: TranslationSettings): TranslationSettings {
        return if (settings.backend == TranslationBackend.ML_KIT && !settings.targetLanguage.isMlKitSupported) {
            settings.copy(backend = TranslationBackend.HY_MT)
        } else {
            settings
        }
    }

    private companion object {
        const val KEY_MODE = "dictation_mode"
        const val KEY_OUTPUT_MODE = "translation_output_mode"
        const val KEY_BACKEND = "translation_backend"
        const val KEY_TARGET_LANGUAGE = "translation_target_language"
    }
}
