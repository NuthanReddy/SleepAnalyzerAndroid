package tech.future.sleepanalyzer.audio.classification

import tech.future.sleepanalyzer.audio.AudioEventType
import tech.future.sleepanalyzer.audio.ClassificationResult
import tech.future.sleepanalyzer.audio.FeatureVector

/**
 * Backward-compatible classifier using only RMS amplitude.
 * Useful as a fallback when feature extraction is unavailable
 * (e.g. low-end devices opting out of FFT).
 */
class AmplitudeClassifier : AudioClassifier {
    override fun classify(features: FeatureVector): ClassificationResult {
        val rms = features.rms
        val peak = features.peak
        val type = when {
            peak >= 14000 -> AudioEventType.COUGH
            peak >= 6500 -> AudioEventType.SNORE
            peak >= 2500 -> AudioEventType.NOISE
            peak <= 600 -> AudioEventType.SILENCE
            else -> AudioEventType.UNKNOWN
        }
        val conf = when (type) {
            AudioEventType.SILENCE -> 1f
            AudioEventType.UNKNOWN -> 0.3f
            else -> (peak / 32768f).coerceIn(0.3f, 0.9f)
        }
        return ClassificationResult(type, conf, features)
    }
}
