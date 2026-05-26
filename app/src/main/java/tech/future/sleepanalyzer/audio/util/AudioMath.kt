package tech.future.sleepanalyzer.audio.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Pure-math helpers reused across the audio pipeline. */
object AudioMath {

    /** Hann window precomputed and cached per length. */
    private val windowCache = java.util.concurrent.ConcurrentHashMap<Int, FloatArray>()
    fun hannWindow(length: Int): FloatArray = windowCache.computeIfAbsent(length) { n ->
        FloatArray(n) { i -> (0.5f - 0.5f * cos(2.0 * PI * i / (n - 1)).toFloat()) }
    }

    fun applyWindow(samples: ShortArray, offset: Int, length: Int, out: FloatArray) {
        val w = hannWindow(length)
        for (i in 0 until length) out[i] = samples[offset + i] * w[i] / 32768f
    }

    fun rms(samples: ShortArray, offset: Int, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        for (i in 0 until length) {
            val s = samples[offset + i].toInt()
            sum += s.toDouble() * s
        }
        return sqrt(sum / length).toFloat()
    }

    fun peak(samples: ShortArray, offset: Int, length: Int): Int {
        var p = 0
        for (i in 0 until length) {
            val v = samples[offset + i].toInt()
            val a = if (v < 0) -v else v
            if (a > p) p = a
        }
        return p
    }

    fun zeroCrossingRate(samples: ShortArray, offset: Int, length: Int): Float {
        if (length < 2) return 0f
        var zc = 0
        var prev = samples[offset].toInt()
        for (i in 1 until length) {
            val cur = samples[offset + i].toInt()
            if ((prev >= 0) != (cur >= 0)) zc++
            prev = cur
        }
        return zc.toFloat() / length
    }

    /**
     * Spectral centroid (mean frequency in Hz weighted by magnitude).
     */
    fun spectralCentroid(magnitudes: FloatArray, sampleRate: Int): Float {
        var num = 0.0
        var den = 0.0
        val n = magnitudes.size
        val freqStep = sampleRate.toDouble() / (2.0 * (n - 1))
        for (i in 0 until n) {
            num += i * freqStep * magnitudes[i]
            den += magnitudes[i]
        }
        return if (den > 0) (num / den).toFloat() else 0f
    }

    fun spectralRolloff(magnitudes: FloatArray, sampleRate: Int, rolloff: Float = 0.85f): Float {
        var total = 0.0
        for (m in magnitudes) total += m
        val target = total * rolloff
        var acc = 0.0
        val freqStep = sampleRate.toDouble() / (2.0 * (magnitudes.size - 1))
        for (i in magnitudes.indices) {
            acc += magnitudes[i]
            if (acc >= target) return (i * freqStep).toFloat()
        }
        return 0f
    }

    fun spectralFlatness(magnitudes: FloatArray): Float {
        var logSum = 0.0
        var sum = 0.0
        var n = 0
        for (m in magnitudes) {
            if (m > 1e-12f) {
                logSum += ln(m.toDouble())
                sum += m
                n++
            }
        }
        if (n == 0 || sum <= 0) return 0f
        val geo = kotlin.math.exp(logSum / n)
        val arith = sum / n
        return (geo / arith).toFloat()
    }

    /**
     * Autocorrelation-based pitch estimate (Hz).
     * Returns 0 if no strong period is detected.
     */
    fun estimatePitch(samples: ShortArray, offset: Int, length: Int, sampleRate: Int,
                      minHz: Float = 40f, maxHz: Float = 500f): Pair<Float, Float> {
        if (length < 64) return 0f to 0f
        val minLag = (sampleRate / maxHz).toInt().coerceAtLeast(1)
        val maxLag = (sampleRate / minHz).toInt().coerceAtMost(length / 2)
        if (minLag >= maxLag) return 0f to 0f

        // Mean removal
        var mean = 0.0
        for (i in 0 until length) mean += samples[offset + i]
        mean /= length
        val s = FloatArray(length)
        for (i in 0 until length) s[i] = (samples[offset + i] - mean).toFloat()

        var bestLag = -1
        var bestVal = 0.0
        var energy0 = 0.0
        for (i in 0 until length) energy0 += s[i].toDouble() * s[i]
        if (energy0 < 1.0) return 0f to 0f

        for (lag in minLag..maxLag) {
            var sum = 0.0
            val n = length - lag
            for (i in 0 until n) sum += s[i].toDouble() * s[i + lag]
            val norm = sum / energy0
            if (norm > bestVal) { bestVal = norm; bestLag = lag }
        }
        if (bestLag < 0) return 0f to 0f
        val pitchHz = sampleRate.toFloat() / bestLag
        val periodicity = max(0.0, bestVal).toFloat().coerceAtMost(1f)
        return pitchHz to periodicity
    }

    /**
     * Compute log-energy in a configurable number of triangular mel-spaced bands.
     */
    fun melBandEnergies(magnitudes: FloatArray, sampleRate: Int, bands: Int): FloatArray {
        val out = FloatArray(bands)
        val lowFreq = 50f
        val highFreq = (sampleRate / 2f).coerceAtMost(8000f)
        val melLow = hzToMel(lowFreq)
        val melHigh = hzToMel(highFreq)
        val melPoints = FloatArray(bands + 2) { i -> melLow + (melHigh - melLow) * i / (bands + 1) }
        val hzPoints = FloatArray(bands + 2) { i -> melToHz(melPoints[i]) }
        val freqStep = sampleRate.toFloat() / (2f * (magnitudes.size - 1))
        for (b in 0 until bands) {
            val fLow = hzPoints[b]
            val fCenter = hzPoints[b + 1]
            val fHigh = hzPoints[b + 2]
            var sum = 0f
            for (k in magnitudes.indices) {
                val f = k * freqStep
                val w = when {
                    f < fLow || f > fHigh -> 0f
                    f <= fCenter -> (f - fLow) / (fCenter - fLow)
                    else -> (fHigh - f) / (fHigh - fCenter)
                }
                sum += magnitudes[k] * w
            }
            out[b] = log10((sum + 1e-6f).toDouble()).toFloat()
        }
        return out
    }

    private fun hzToMel(hz: Float) = 2595f * log10(1f + hz / 700f).toFloat()
    private fun melToHz(mel: Float) = 700f * (Math.pow(10.0, (mel / 2595f).toDouble()).toFloat() - 1f)

    /** Cosine similarity between two equal-length feature vectors. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat().coerceIn(-1f, 1f)
    }
}
