package com.typetype.droid

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import com.typetype.droid.asr.SherpaAsrEngineFactory
import com.typetype.droid.session.DictationMode
import com.typetype.droid.settings.VoiceImePreferences
import com.typetype.droid.translation.TranslationBackend
import com.typetype.droid.translation.FallbackTranslationEngine
import com.typetype.droid.translation.HyMtTranslationEngine
import com.typetype.droid.translation.TranslationEngine
import com.typetype.droid.translation.MlKitTranslationEngine
import com.typetype.droid.translation.TranslationOutputMode
import com.typetype.droid.translation.TranslationTargetLanguage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TypeTypeApplication : Application() {
    lateinit var asrEngineFactory: SherpaAsrEngineFactory
        private set
    lateinit var hyMtTranslationEngine: TranslationEngine
        private set
    lateinit var mlKitTranslationEngine: TranslationEngine
        private set

    private val preloadExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "TypeTypeModelPreload").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    override fun onCreate() {
        super.onCreate()
        TypeTypeReturnNotification.hide(this)
        if (isFloatingProcess()) {
            return
        }
        FloatingImeSwitcherService.startIfAllowed(this)
        asrEngineFactory = SherpaAsrEngineFactory(assets)
        hyMtTranslationEngine = HyMtTranslationEngine(this)
        mlKitTranslationEngine = MlKitTranslationEngine()
        val preferences = VoiceImePreferences(this)
        warmUpAsr(preferences.loadEffectiveMode())
        val translationSettings = preferences.loadTranslationSettings()
        if (translationSettings.outputMode == TranslationOutputMode.TRANSLATION) {
            warmUpTranslation(translationSettings.backend, translationSettings.targetLanguage)
        }
    }

    fun warmUpAsr(mode: DictationMode) {
        preloadExecutor.execute {
            runCatching {
                asrEngineFactory.warmUp(mode)
            }
        }
    }

    fun warmUpTranslation(
        backend: TranslationBackend,
        targetLanguage: TranslationTargetLanguage,
    ) {
        if (backend == TranslationBackend.HY_MT) {
            return
        }
        preloadExecutor.execute {
            runCatching {
                translationEngineFor(backend).warmUp(targetLanguage)
            }
        }
    }

    fun translationEngineFor(backend: TranslationBackend): TranslationEngine {
        return when (backend) {
            TranslationBackend.HY_MT -> FallbackTranslationEngine(hyMtTranslationEngine, mlKitTranslationEngine)
            TranslationBackend.ML_KIT -> mlKitTranslationEngine
        }
    }

    fun close() {
        preloadExecutor.shutdownNow()
        hyMtTranslationEngine.close()
        mlKitTranslationEngine.close()
        asrEngineFactory.close()
    }

    private fun isFloatingProcess(): Boolean {
        return currentProcessName()?.endsWith(":floating") == true
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName()
        }
        val pid = android.os.Process.myPid()
        val activityManager = getSystemService(ActivityManager::class.java) ?: return null
        return activityManager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
    }
}
