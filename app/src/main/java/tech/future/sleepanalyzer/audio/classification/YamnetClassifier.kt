package tech.future.sleepanalyzer.audio.classification

import android.content.Context
import android.util.Log
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier as TfAudioClassifier
import org.tensorflow.lite.task.core.BaseOptions
import tech.future.sleepanalyzer.audio.AudioEventType
import tech.future.sleepanalyzer.audio.ClassificationResult
import tech.future.sleepanalyzer.audio.FeatureVector
import kotlin.math.max

/**
 * On-device sound classifier backed by YAMNet (MobileNet trained on AudioSet) running through the
 * TensorFlow Lite Task Library. Replaces the hand-tuned [SpectralClassifier] with a model that has
 * dedicated "Cough", "Snoring" and "Speech" classes, which is far more robust than absolute
 * amplitude/flatness thresholds.
 *
 * YAMNet needs the full waveform at 16 kHz, so this classifier only does real work through the raw
 * PCM entry point; the feature-only [classify] path returns UNKNOWN. The model runs on 0.975 s
 * windows, so an event longer than that is scanned window-by-window and the strongest score per
 * class is kept — a cough anywhere in the clip is caught, not just its first 46 ms.
 */
class YamnetClassifier private constructor(
    private val classifier: TfAudioClassifier
) : AudioClassifier {

    private val format = classifier.requiredTensorAudioFormat
    private val modelSampleRate = format.sampleRate
    private val windowSamples = classifier.requiredInputBufferSize.toInt().coerceAtLeast(1)
    private val lock = Any()

    /** No waveform available on this path, so the model can't run. Report UNKNOWN. */
    override fun classify(features: FeatureVector): ClassificationResult =
        ClassificationResult(AudioEventType.UNKNOWN, 0f, features)

    override fun classify(
        pcm: ShortArray,
        offset: Int,
        length: Int,
        sampleRate: Int,
        features: FeatureVector
    ): ClassificationResult {
        if (cheapPrefilter(features.rms, features.peak) == AudioEventType.SILENCE) {
            return ClassificationResult(AudioEventType.SILENCE, 1f, features)
        }

        val mono = if (offset == 0 && length == pcm.size) pcm else pcm.copyOfRange(offset, offset + length)
        val samples = if (sampleRate == modelSampleRate) mono
            else resampleLinear(mono, sampleRate, modelSampleRate)
        if (samples.isEmpty()) return ClassificationResult(AudioEventType.UNKNOWN, 0.3f, features)

        val groupScores = HashMap<AudioEventType, Float>()
        var silenceScore = 0f
        val hop = (windowSamples / 2).coerceAtLeast(1)
        var start = 0
        var windows = 0
        try {
            while (start < samples.size && windows < MAX_WINDOWS) {
                val end = minOf(start + windowSamples, samples.size)
                val window = ShortArray(windowSamples)
                System.arraycopy(samples, start, window, 0, end - start)

                val categories = synchronized(lock) {
                    val tensor = TensorAudio.create(format, windowSamples)
                    tensor.load(window)
                    classifier.classify(tensor)
                }.firstOrNull()?.categories ?: emptyList()

                for (c in categories) {
                    if (c.index == YamnetLabels.SILENCE_INDEX) silenceScore = max(silenceScore, c.score)
                    val group = YamnetLabels.groupFor(c.index) ?: continue
                    groupScores[group] = max(groupScores[group] ?: 0f, c.score)
                }

                if (end == samples.size) break
                start += hop
                windows++
            }
        } catch (t: Throwable) {
            Log.w(TAG, "YAMNet inference failed; treating event as unknown", t)
            return ClassificationResult(AudioEventType.UNKNOWN, 0.3f, features)
        }

        val (type, confidence) = YamnetLabels.decide(groupScores, silenceScore, features.rms, features.peak)
        return ClassificationResult(type, confidence, features)
    }

    fun close() = runCatching { classifier.close() }

    /** Linear resampler; adequate for feeding a sound-event classifier. */
    private fun resampleLinear(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (input.isEmpty() || srcRate == dstRate) return input
        val outLen = (input.size.toLong() * dstRate / srcRate).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        val ratio = srcRate.toDouble() / dstRate
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val idx = srcPos.toInt()
            val frac = srcPos - idx
            val a = input[idx].toInt()
            val b = if (idx + 1 < input.size) input[idx + 1].toInt() else a
            out[i] = (a + (b - a) * frac).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    companion object {
        private const val TAG = "YamnetClassifier"
        private const val MODEL_ASSET = "yamnet.tflite"
        private const val MAX_WINDOWS = 12

        /** Loads the model from assets. Returns null if it can't be loaded so callers can fall back. */
        fun create(context: Context): YamnetClassifier? = runCatching {
            val options = TfAudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setNumThreads(2).build())
                .setMaxResults(MAX_RESULTS)
                .build()
            YamnetClassifier(TfAudioClassifier.createFromFileAndOptions(context, MODEL_ASSET, options))
        }.onFailure { Log.w(TAG, "Failed to load YAMNet model; falling back to heuristics", it) }
            .getOrNull()

        // Enough top classes to surface a target sound even when generic ambience scores higher.
        private const val MAX_RESULTS = 25
    }
}
