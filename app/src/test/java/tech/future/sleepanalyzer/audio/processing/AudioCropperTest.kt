package tech.future.sleepanalyzer.audio.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCropperTest {

    @Test
    fun `merged event keeps first and last active bursts`() {
        val sampleRate = 8_000
        val windowSize = sampleRate * 30 / 1_000
        val samples = ShortArray(windowSize * 18) { 50 }
        fillWindows(samples, windowSize, 3 until 6, 1_000)
        fillWindows(samples, windowSize, 11 until 14, 1_500)

        val result = AudioCropper.cropActiveSpan(
            samples = samples,
            sampleRate = sampleRate,
            paddingMs = 0
        )

        assertEquals(90, result.startMs)
        assertEquals(420, result.endMs)
        assertTrue(result.pcm.any { it.toInt() == 1_000 })
        assertTrue(result.pcm.any { it.toInt() == 1_500 })
    }

    private fun fillWindows(
        samples: ShortArray,
        windowSize: Int,
        windows: IntRange,
        amplitude: Int
    ) {
        for (window in windows) {
            val start = window * windowSize
            for (index in start until start + windowSize) {
                samples[index] = if (index % 2 == 0) amplitude.toShort() else (-amplitude).toShort()
            }
        }
    }
}
