package com.typetype.droid.translation

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.ModelLoadException
import com.arm.aichat.UnsupportedArchitectureException
import com.arm.aichat.gguf.GgufMetadataReader
import com.arm.aichat.gguf.InvalidFileFormatException
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class HyMtTranslationEngine(
    context: Context,
) : TranslationEngine {
    private val appContext = context.applicationContext
    private val engineDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AiChat.getInferenceEngine(appContext)
    }
    private val engine: InferenceEngine by engineDelegate
    private val ggufReader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GgufMetadataReader.create()
    }
    private val lock = Any()
    private var loadedModelPath: String? = null
    private val modelCopied = AtomicBoolean(false)

    override fun warmUp(targetLanguage: TranslationTargetLanguage) {
        try {
            ensureModelLoaded()
        } catch (error: Throwable) {
            throw wrapHyMtError("warm-up", error)
        }
    }

    override fun translate(text: String, targetLanguage: TranslationTargetLanguage): String {
        val normalized = text.trim()
        if (normalized.isEmpty()) return ""

        try {
            val startMs = elapsedRealtimeOrZero()
            logInfo("HY-MT2 translation start target=${targetLanguage.name} chars=${normalized.length}")
            ensureModelLoaded()
            val translated = runBlocking {
                val output = StringBuilder()
                withTimeout(GENERATION_TIMEOUT_MS) {
                    engine.sendUserPrompt(buildUserPrompt(normalized, targetLanguage), predictLength = PREDICT_LENGTH)
                        .collect { token ->
                            output.append(token)
                        }
                }
                output.toString().trim().ifEmpty {
                    throw IOException("HY-MT2 generated empty output")
                }
            }
            val normalizedTranslation = CantoneseTranslationPostProcessor.normalize(translated, targetLanguage)
            logInfo(
                "HY-MT2 translation done elapsed=${elapsedRealtimeOrZero() - startMs}ms chars=${normalizedTranslation.length}",
            )
            return normalizedTranslation
        } catch (error: Throwable) {
            logError("HY-MT2 translation failed", error)
            throw wrapHyMtError("translation", error)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (engineDelegate.isInitialized()) {
                runCatching { engine.destroy() }
            }
            loadedModelPath = null
            modelCopied.set(false)
        }
    }

    private fun ensureModelLoaded() {
        synchronized(lock) {
            if (loadedModelPath != null) return

            val startMs = elapsedRealtimeOrZero()
            logInfo("HY-MT2 ensureModelLoaded start")
            val modelFile = ensureBundledModelCopied()
            logInfo("HY-MT2 model file ready elapsed=${elapsedRealtimeOrZero() - startMs}ms size=${modelFile.length()}")
            validateModelFile(modelFile)
            logInfo("HY-MT2 model file validated elapsed=${elapsedRealtimeOrZero() - startMs}ms")
            runBlocking {
                withTimeout(MODEL_LOAD_TIMEOUT_MS) {
                    resetErroredEngineIfNeeded()
                    awaitEngineInitialized()
                    if (loadedModelPath != modelFile.absolutePath) {
                        runCatching { engine.cleanUp() }
                        engine.loadModel(modelFile.absolutePath)
                        loadedModelPath = modelFile.absolutePath
                    }
                }
            }
            logInfo("HY-MT2 model loaded elapsed=${elapsedRealtimeOrZero() - startMs}ms")
        }
    }

    private fun ensureBundledModelCopied(): File {
        val targetDir = File(appContext.filesDir, MODEL_DIR_NAME).apply { mkdirs() }
        val targetFile = File(targetDir, MODEL_FILE_NAME)
        deleteLegacyModelFiles(targetDir)
        if (modelCopied.get() && isUsableModelFile(targetFile)) {
            return targetFile
        }
        synchronized(lock) {
            if (modelCopied.get() && isUsableModelFile(targetFile)) {
                return targetFile
            }

            if (isUsableModelFile(targetFile)) {
                modelCopied.set(true)
                return targetFile
            }

            val tempFile = File(targetDir, "$MODEL_FILE_NAME.tmp")
            tempFile.delete()
            appContext.assets.open(MODEL_ASSET_PATH).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && !targetFile.delete()) {
                tempFile.delete()
                throw IOException("Failed to replace stale HY-MT2 model file")
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                throw IOException("Failed to finalize HY-MT2 model copy")
            }
            modelCopied.set(true)
            return targetFile
        }
    }

    private fun deleteLegacyModelFiles(targetDir: File) {
        LEGACY_MODEL_FILE_NAMES.forEach { fileName ->
            runCatching {
                File(targetDir, fileName)
                    .takeIf { it.exists() && it.name != MODEL_FILE_NAME }
                    ?.delete()
            }
        }
    }

    private suspend fun awaitEngineInitialized() {
        val state = engine.state
            .filter {
                it is InferenceEngine.State.Initialized ||
                    it is InferenceEngine.State.ModelReady ||
                    it is InferenceEngine.State.Error
            }
            .first()

        if (state is InferenceEngine.State.Error) {
            throw state.exception
        }
    }

    private fun resetErroredEngineIfNeeded() {
        if (engine.state.value is InferenceEngine.State.Error) {
            runCatching { engine.cleanUp() }
        }
    }

    private fun isUsableModelFile(file: File): Boolean {
        return file.exists() && file.isFile && file.length() == MODEL_EXPECTED_SIZE_BYTES
    }

    private fun validateModelFile(file: File) {
        if (file.length() != MODEL_EXPECTED_SIZE_BYTES) {
            throw IOException(
                "HY-MT2 model size mismatch: expected=$MODEL_EXPECTED_SIZE_BYTES actual=${file.length()}",
            )
        }
        runBlocking {
            try {
                ggufReader.ensureSourceFileFormat(file)
            } catch (_: InvalidFileFormatException) {
                throw IOException("HY-MT2 model is not a valid GGUF file")
            }
        }
    }

    private fun buildUserPrompt(
        text: String,
        targetLanguage: TranslationTargetLanguage,
    ): String {
        return buildString {
            append("将以下文本翻译为")
            append(targetLanguage.hyMtTargetLabel)
            append("，注意只需要输出翻译后的结果，不要额外解释：\n\n")
            append(text)
        }
    }

    private fun wrapHyMtError(
        phase: String,
        error: Throwable,
    ): RuntimeException {
        val detail = when (error) {
            is UnsupportedArchitectureException -> "device architecture is unsupported"
            is ModelLoadException -> "native model load returned code=${error.code}"
            else -> error.message ?: error.javaClass.simpleName
        }
        return RuntimeException("HY-MT2 $phase failed: $detail", error)
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

    private companion object {
        const val TAG = "HyMtTranslationEngine"
        const val MODEL_DIR_NAME = "translation-models"
        const val MODEL_FILE_NAME = "Hy-MT2-1.8B-Q4_K_M.gguf"
        const val MODEL_ASSET_PATH = "translation-models/Hy-MT2-1.8B-Q4_K_M.gguf"
        const val MODEL_EXPECTED_SIZE_BYTES = 1_133_080_448L
        const val PREDICT_LENGTH = 160
        const val MODEL_LOAD_TIMEOUT_MS = 180_000L
        const val GENERATION_TIMEOUT_MS = 120_000L
        val LEGACY_MODEL_FILE_NAMES = setOf(
            "HY-MT1.5-1.8B-Q4_K_M.gguf",
            "Hy-MT1.5-1.8B-2bit.gguf",
        )
    }
}
