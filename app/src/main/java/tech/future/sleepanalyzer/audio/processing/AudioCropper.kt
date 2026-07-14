package tech.future.sleepanalyzer.audio.processing

import tech.future.sleepanalyzer.audio.util.AudioMath

/**
 * Trims a PCM buffer to the active region by scanning RMS in fixed windows
 * and keeping the complete span from the first active window through the last.
 * This preserves every burst in an event joined by the configured merge gap.
 */
object AudioCropper {

    data class CropResult(
        val pcm: ShortArray,
        val startMs: Int,
        val endMs: Int
    )

    fun cropActiveSpan(
        samples: ShortArray,
        sampleRate: Int,
        windowMs: Int = 30,
        paddingMs: Int = 400,
        thresholdMultiplier: Float = 1.8f
    ): CropResult {
        if (samples.isEmpty()) return CropResult(samples, 0, 0)
        val windowSize = (sampleRate * windowMs / 1000).coerceAtLeast(64)
        val windows = (samples.size + windowSize - 1) / windowSize
        if (windows < 3) return CropResult(samples, 0, samples.size * 1000 / sampleRate)

        val rmsArr = FloatArray(windows)
        for (i in 0 until windows) {
            val offset = i * windowSize
            rmsArr[i] = AudioMath.rms(samples, offset, minOf(windowSize, samples.size - offset))
        }

        // Adaptive threshold: median of lower half * multiplier
        val sorted = rmsArr.sortedArray()
        val noiseEstimate = sorted[(sorted.size * 0.3f).toInt().coerceAtMost(sorted.size - 1)]
        val threshold = (noiseEstimate * thresholdMultiplier).coerceAtLeast(150f)

        var firstActive = -1
        var lastActiveExclusive = -1
        for (i in 0 until windows) {
            if (rmsArr[i] >= threshold) {
                if (firstActive < 0) firstActive = i
                lastActiveExclusive = i + 1
            }
        }
        if (firstActive < 0) {
            return CropResult(ShortArray(0), 0, 0)
        }
        val paddingWindows = (paddingMs / windowMs).coerceAtLeast(0)
        val s = (firstActive - paddingWindows).coerceAtLeast(0) * windowSize
        val e = minOf(
            samples.size,
            (lastActiveExclusive + paddingWindows).coerceAtMost(windows) * windowSize
        )
        val outLen = (e - s).coerceAtLeast(0)
        return CropResult(
            pcm = samples.copyOfRange(s, s + outLen),
            startMs = s * 1000 / sampleRate,
            endMs = e * 1000 / sampleRate
        )
    }
}
