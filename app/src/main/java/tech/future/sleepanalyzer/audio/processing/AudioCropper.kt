package tech.future.sleepanalyzer.audio.processing

import tech.future.sleepanalyzer.audio.util.AudioMath

/**
 * Trims a PCM buffer to the active region by scanning RMS in fixed windows
 * and keeping the largest contiguous span above an adaptive threshold,
 * with configurable padding around the boundaries.
 */
object AudioCropper {

    data class CropResult(
        val pcm: ShortArray,
        val startMs: Int,
        val endMs: Int
    )

    fun crop(
        samples: ShortArray,
        sampleRate: Int,
        windowMs: Int = 30,
        paddingMs: Int = 400,
        thresholdMultiplier: Float = 1.8f
    ): CropResult {
        if (samples.isEmpty()) return CropResult(samples, 0, 0)
        val windowSize = (sampleRate * windowMs / 1000).coerceAtLeast(64)
        val windows = samples.size / windowSize
        if (windows < 3) return CropResult(samples, 0, samples.size * 1000 / sampleRate)

        val rmsArr = FloatArray(windows)
        for (i in 0 until windows) rmsArr[i] = AudioMath.rms(samples, i * windowSize, windowSize)

        // Adaptive threshold: median of lower half * multiplier
        val sorted = rmsArr.sortedArray()
        val noiseEstimate = sorted[(sorted.size * 0.3f).toInt().coerceAtMost(sorted.size - 1)]
        val threshold = (noiseEstimate * thresholdMultiplier).coerceAtLeast(150f)

        // Find longest contiguous active run
        var bestStart = -1; var bestEnd = -1; var bestLen = 0
        var curStart = -1
        for (i in 0 until windows) {
            if (rmsArr[i] >= threshold) {
                if (curStart < 0) curStart = i
            } else if (curStart >= 0) {
                val len = i - curStart
                if (len > bestLen) { bestLen = len; bestStart = curStart; bestEnd = i }
                curStart = -1
            }
        }
        if (curStart >= 0) {
            val len = windows - curStart
            if (len > bestLen) { bestLen = len; bestStart = curStart; bestEnd = windows }
        }
        if (bestStart < 0) {
            return CropResult(ShortArray(0), 0, 0)
        }
        val paddingWindows = (paddingMs / windowMs).coerceAtLeast(1)
        val s = (bestStart - paddingWindows).coerceAtLeast(0) * windowSize
        val e = ((bestEnd + paddingWindows).coerceAtMost(windows)) * windowSize
        val outLen = (e - s).coerceAtLeast(0)
        val out = ShortArray(outLen)
        System.arraycopy(samples, s, out, 0, outLen)
        return CropResult(
            pcm = out,
            startMs = s * 1000 / sampleRate,
            endMs = e * 1000 / sampleRate
        )
    }
}
