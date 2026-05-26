package tech.future.sleepanalyzer.audio.processing

import tech.future.sleepanalyzer.audio.FeatureVector
import tech.future.sleepanalyzer.audio.util.AudioMath
import tech.future.sleepanalyzer.audio.util.FftProcessor

/**
 * Stateless feature extractor for one PCM window. Reuses pooled FFT buffers
 * to avoid per-frame allocations on the hot path.
 *
 * `fftSize` must be a power of two; 1024 covers ~46 ms at 22050 Hz.
 */
class AudioFeatures(
    val sampleRate: Int,
    val fftSize: Int = 1024,
    val melBands: Int = 13
) {
    private val fft = FftProcessor.forSize(fftSize)
    private val windowed = FloatArray(fftSize)
    private val magnitudes = FloatArray(fftSize / 2 + 1)

    /**
     * Fills [out] with features extracted from [samples] (mono PCM).
     * Returns the same [out] instance for chaining.
     */
    fun extract(samples: ShortArray, offset: Int, length: Int, out: FeatureVector): FeatureVector {
        out.reset()
        val take = minOf(length, fftSize)
        out.rms = AudioMath.rms(samples, offset, length)
        out.peak = AudioMath.peak(samples, offset, length)
        out.zeroCrossingRate = AudioMath.zeroCrossingRate(samples, offset, length)

        // Windowed FFT for spectral features
        if (take >= 64) {
            AudioMath.applyWindow(samples, offset, take, windowed)
            for (i in take until fftSize) windowed[i] = 0f
            fft.magnitudeSpectrum(windowed, magnitudes)
            out.spectralCentroid = AudioMath.spectralCentroid(magnitudes, sampleRate)
            out.spectralRolloff = AudioMath.spectralRolloff(magnitudes, sampleRate)
            out.spectralFlatness = AudioMath.spectralFlatness(magnitudes)
            val bands = AudioMath.melBandEnergies(magnitudes, sampleRate, melBands)
            System.arraycopy(bands, 0, out.bandEnergies, 0, minOf(bands.size, out.bandEnergies.size))
        }

        val (pitch, periodicity) = AudioMath.estimatePitch(samples, offset, take, sampleRate)
        out.pitchHz = pitch
        out.periodicity = periodicity
        return out
    }
}
