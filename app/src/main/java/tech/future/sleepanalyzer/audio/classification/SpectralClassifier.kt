package tech.future.sleepanalyzer.audio.classification

import tech.future.sleepanalyzer.audio.AudioEventType
import tech.future.sleepanalyzer.audio.ClassificationResult
import tech.future.sleepanalyzer.audio.FeatureVector

/**
 * Heuristic spectral classifier. Uses periodicity, pitch, spectral flatness,
 * and zero-crossing rate to decide between snore, cough, talk and noise.
 *
 * Designed to be replaced by a TFLite model later via [AudioClassifier].
 */
class SpectralClassifier : AudioClassifier {

    override fun classify(features: FeatureVector): ClassificationResult {
        val rms = features.rms
        val peak = features.peak
        val pre = cheapPrefilter(rms, peak)
        if (pre == AudioEventType.SILENCE) {
            return ClassificationResult(AudioEventType.SILENCE, 1f, features)
        }

        val zcr = features.zeroCrossingRate
        val pitch = features.pitchHz
        val periodicity = features.periodicity
        val flatness = features.spectralFlatness
        val centroid = features.spectralCentroid

        // COUGH: short burst, broadband, high peak, high flatness, low pitch periodicity
        if (peak > 18000 && flatness > 0.35f && periodicity < 0.4f && zcr > 0.05f) {
            val confidence = ((peak - 18000) / 14000f).coerceIn(0f, 1f) * 0.6f +
                (flatness - 0.35f) * 1.2f
            return ClassificationResult(AudioEventType.COUGH, confidence.coerceIn(0.5f, 0.99f), features)
        }

        // SNORE: rhythmic, low-pitched, harmonic, moderate energy, centroid below 1500 Hz
        if (pitch in 40f..220f && periodicity > 0.45f && centroid < 1500f && flatness < 0.4f) {
            val confidence = (periodicity - 0.45f) * 1.8f + (0.4f - flatness) * 0.5f
            return ClassificationResult(AudioEventType.SNORE, confidence.coerceIn(0.5f, 0.99f), features)
        }

        // TALK: pitch in voice range, variable spectrum, higher centroid, formant-like flatness
        if (pitch in 80f..420f && centroid in 700f..3500f && periodicity > 0.25f && flatness < 0.55f) {
            val confidence = (periodicity * 0.6f) + ((3500f - centroid) / 3500f).coerceIn(0f, 1f) * 0.2f
            return ClassificationResult(AudioEventType.TALK, confidence.coerceIn(0.5f, 0.95f), features)
        }

        // Loud but unclassified -> NOISE
        if (rms > 500f || peak > 6000) {
            val confidence = (rms / 4000f).coerceIn(0.3f, 0.85f)
            return ClassificationResult(AudioEventType.NOISE, confidence, features)
        }

        return ClassificationResult(AudioEventType.UNKNOWN, 0.3f, features)
    }
}
