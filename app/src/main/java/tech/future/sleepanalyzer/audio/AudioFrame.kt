package tech.future.sleepanalyzer.audio

/**
 * A short window of 16-bit PCM audio captured from the microphone.
 * `samples` is a recycled buffer owned by the producer; consumers must finish
 * processing before yielding or copy what they need.
 */
data class AudioFrame(
    val samples: ShortArray,
    val length: Int,
    val sampleRateHz: Int,
    val startTimeMs: Long,
    val sequence: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return sequence == other.sequence && sampleRateHz == other.sampleRateHz
    }
    override fun hashCode(): Int = sequence.hashCode() * 31 + sampleRateHz
}

/**
 * Extracted features for a frame or aggregated window of frames.
 * Mutable for object pooling efficiency in the processing pipeline.
 */
data class FeatureVector(
    var rms: Float = 0f,
    var peak: Int = 0,
    var zeroCrossingRate: Float = 0f,
    var spectralCentroid: Float = 0f,
    var spectralRolloff: Float = 0f,
    var spectralFlatness: Float = 0f,
    var pitchHz: Float = 0f,
    var periodicity: Float = 0f,
    /** 13 log-mel-like band energies for matching. */
    var bandEnergies: FloatArray = FloatArray(13)
) {
    fun reset() {
        rms = 0f; peak = 0; zeroCrossingRate = 0f
        spectralCentroid = 0f; spectralRolloff = 0f; spectralFlatness = 0f
        pitchHz = 0f; periodicity = 0f
        for (i in bandEnergies.indices) bandEnergies[i] = 0f
    }
}

/** Classifier output. */
data class ClassificationResult(
    val type: AudioEventType,
    val confidence: Float,
    val features: FeatureVector
)

/** Voice matcher output. */
data class AttributionResult(
    val attribution: Attribution,
    val confidence: Float
)
