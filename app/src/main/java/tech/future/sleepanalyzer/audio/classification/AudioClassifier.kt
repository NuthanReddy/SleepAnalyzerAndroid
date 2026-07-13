package tech.future.sleepanalyzer.audio.classification

import tech.future.sleepanalyzer.audio.AudioEventType
import tech.future.sleepanalyzer.audio.ClassificationResult
import tech.future.sleepanalyzer.audio.FeatureVector

/**
 * Strategy interface for audio event classification.
 * Implementations must be pure with respect to the input features
 * so they can run on any thread in the processing pool.
 */
interface AudioClassifier {
    fun classify(features: FeatureVector): ClassificationResult

    /**
     * Classify an event from its raw cropped PCM. Model-based classifiers (e.g. YAMNet) need the
     * whole waveform, not the summary [features]. Feature-only implementations can ignore [pcm]
     * and fall back to [classify]; the default does exactly that so existing classifiers keep
     * working unchanged.
     */
    fun classify(
        pcm: ShortArray,
        offset: Int,
        length: Int,
        sampleRate: Int,
        features: FeatureVector
    ): ClassificationResult = classify(features)

    /** Hint about the most likely event using only RMS, for cheap pre-filtering. */
    fun cheapPrefilter(rms: Float, peak: Int): AudioEventType =
        when {
            peak <= 800 && rms <= 150 -> AudioEventType.SILENCE
            else -> AudioEventType.UNKNOWN
        }
}
