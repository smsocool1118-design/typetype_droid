package com.typetype.droid.asr

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.getEndpointConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getModelConfig
import com.k2fsa.sherpa.onnx.getOfflineModelConfig
import com.typetype.droid.audio.AudioCaptureEngine
import com.typetype.droid.session.DictationMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SherpaAsrEngineFactory(
    private val assetManager: AssetManager,
) : AsrEngineFactory {
    private val lock = Any()
    private var streamingRecognizer: OnlineRecognizer? = null
    private var offlineRecognizer: OfflineRecognizer? = null
    private var closed = false

    override fun create(mode: DictationMode, onEvent: (AsrEvent) -> Unit): AsrEngine {
        check(!closed) { "ASR engine factory is closed" }
        return when (mode) {
            DictationMode.STREAMING -> SherpaStreamingAsrEngine(
                recognizer = streamingRecognizer(),
                onEvent = onEvent,
            )

            DictationMode.OFFLINE -> SherpaOfflineAsrEngine(
                recognizer = offlineRecognizer(),
                onEvent = onEvent,
            )
        }
    }

    fun warmUp(mode: DictationMode) {
        check(!closed) { "ASR engine factory is closed" }
        when (mode) {
            DictationMode.STREAMING -> streamingRecognizer()
            DictationMode.OFFLINE -> offlineRecognizer()
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            streamingRecognizer?.release()
            streamingRecognizer = null
            offlineRecognizer?.release()
            offlineRecognizer = null
        }
    }

    private fun streamingRecognizer(): OnlineRecognizer {
        synchronized(lock) {
            return streamingRecognizer ?: OnlineRecognizer(
                assetManager = assetManager,
                config = OnlineRecognizerConfig(
                    featConfig = getFeatureConfig(AudioCaptureEngine.SAMPLE_RATE, featureDim = 80),
                    modelConfig = getModelConfig(STREAMING_ZH_MODEL_TYPE)
                        ?: error("Missing streaming sherpa-onnx model config"),
                    endpointConfig = getEndpointConfig(),
                    enableEndpoint = true,
                    hotwordsFile = HOTWORDS_ASSET_PATH,
                    hotwordsScore = HOTWORDS_SCORE,
                ),
            ).also { streamingRecognizer = it }
        }
    }

    private fun offlineRecognizer(): OfflineRecognizer {
        synchronized(lock) {
            return offlineRecognizer ?: OfflineRecognizer(
                assetManager = assetManager,
                config = OfflineRecognizerConfig(
                    featConfig = getFeatureConfig(AudioCaptureEngine.SAMPLE_RATE, featureDim = 80),
                    modelConfig = getOfflineModelConfig(OFFLINE_ZH_MODEL_TYPE)
                        ?: error("Missing offline sherpa-onnx model config"),
                    hotwordsFile = HOTWORDS_ASSET_PATH,
                    hotwordsScore = HOTWORDS_SCORE,
                ),
            ).also { offlineRecognizer = it }
        }
    }

}

private class SherpaStreamingAsrEngine(
    private val recognizer: OnlineRecognizer,
    private val onEvent: (AsrEvent) -> Unit,
) : AsrEngine {
    private var stream = recognizer.createStream()
    private val closed = AtomicBoolean(false)
    private val generation = AtomicInteger(0)

    private var lastTextEventTimeMs = 0L
    private var pendingText: String? = null

    @SuppressLint("DefaultLocale")
    override fun acceptSamples(samples: FloatArray) {
        if (closed.get()) return
        val currentGeneration = generation.get()

        stream.acceptWaveform(samples, AudioCaptureEngine.SAMPLE_RATE)

        val result: String
        val shouldReset: Boolean
        synchronized(this) {
            if (closed.get()) return
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
            if (closed.get() || currentGeneration != generation.get()) return
            result = recognizer.getResult(stream).text
            shouldReset = recognizer.isEndpoint(stream)
            if (shouldReset) {
                recognizer.reset(stream)
            }
        }

        if (result.isNotBlank()) {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastTextEventTimeMs
            if (elapsed >= MIN_TEXT_EVENT_INTERVAL_MS) {
                onEvent(AsrEvent.Text(result))
                lastTextEventTimeMs = now
                pendingText = null
            } else {
                pendingText = result
            }
        }
        if (shouldReset) {
            pendingText?.let { onEvent(AsrEvent.Text(it)) }
            pendingText = null
            lastTextEventTimeMs = SystemClock.elapsedRealtime()
            onEvent(AsrEvent.SegmentFinished)
        }
    }

    override fun finish() {
        if (closed.get()) return
        val result: String
        synchronized(this) {
            if (closed.get()) return
            stream.inputFinished()
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
            result = recognizer.getResult(stream).text
        }
        if (result.isNotBlank()) {
            onEvent(AsrEvent.Text(result))
        }
        onEvent(AsrEvent.SegmentFinished)
    }

    override fun reset() {
        synchronized(this) {
            if (closed.get()) return
            generation.incrementAndGet()
            stream.release()
            stream = recognizer.createStream()
        }
    }

    override fun close() {
        synchronized(this) {
            if (!closed.compareAndSet(false, true)) return
            stream.release()
        }
    }
}

private class SherpaOfflineAsrEngine(
    private val recognizer: OfflineRecognizer,
    private val onEvent: (AsrEvent) -> Unit,
) : AsrEngine {
    private val closed = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val lock = Any()
    private val chunks = mutableListOf<FloatArray>()
    private var sampleCount = 0

    override fun acceptSamples(samples: FloatArray) {
        if (closed.get()) return
        synchronized(lock) {
            if (closed.get()) return
            chunks += samples.copyOf()
            sampleCount += samples.size
        }
    }

    override fun finish() {
        if (closed.get()) return
        val currentGeneration = generation.get()
        val samples = drainSamples()
        if (samples.isEmpty()) {
            onEvent(AsrEvent.SegmentFinished)
            return
        }
        decodeSamples(samples, currentGeneration)
    }

    override fun reset() {
        if (closed.get()) return
        generation.incrementAndGet()
        synchronized(lock) {
            chunks.clear()
            sampleCount = 0
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) {
            chunks.clear()
            sampleCount = 0
        }
    }

    private fun drainSamples(): FloatArray {
        synchronized(lock) {
            if (sampleCount == 0) return FloatArray(0)
            val output = FloatArray(sampleCount)
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(output, destinationOffset = offset)
                offset += chunk.size
            }
            chunks.clear()
            sampleCount = 0
            return output
        }
    }

    private fun decodeSamples(samples: FloatArray, decodeGeneration: Int) {
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, AudioCaptureEngine.SAMPLE_RATE)
        recognizer.decode(stream)
        val text = recognizer.getResult(stream).text
        stream.release()
        if (!closed.get() && decodeGeneration == generation.get() && text.isNotBlank()) {
            onEvent(AsrEvent.Text(text))
            onEvent(AsrEvent.SegmentFinished)
        }
    }
}

private const val STREAMING_ZH_MODEL_TYPE = 15
private const val OFFLINE_ZH_MODEL_TYPE = 41
private const val MIN_TEXT_EVENT_INTERVAL_MS = 100L
private const val HOTWORDS_ASSET_PATH = "hotwords_zh_cn.txt"
private const val HOTWORDS_SCORE = 2.0f
