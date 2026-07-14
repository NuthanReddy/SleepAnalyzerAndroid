package tech.future.sleepanalyzer.audio.processing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.future.sleepanalyzer.audio.AudioFrame

class VoiceActivityDetectorTest {

    @Test
    fun `startup calibration absorbs steady room noise`() {
        val detector = VoiceActivityDetector(calibrationFrames = 10)

        repeat(10) { detector.process(frame(amplitude = 400, sequence = it.toLong())) }

        assertFalse(detector.lastIsVoice)
        assertTrue(detector.noiseFloor > 350f)
        assertTrue(detector.process(frame(amplitude = 400, sequence = 11)) == null)
    }

    @Test
    fun `sustained sound remains active after calibration`() {
        val detector = VoiceActivityDetector(calibrationFrames = 5)
        repeat(5) { detector.process(frame(amplitude = 100, sequence = it.toLong())) }

        repeat(400) { index ->
            assertTrue(detector.process(frame(amplitude = 1_000, sequence = (index + 5).toLong())) != null)
        }
        assertTrue(detector.lastIsVoice)
        assertTrue(detector.noiseFloor < 250f)
    }

    private fun frame(amplitude: Int, sequence: Long): AudioFrame {
        val samples = ShortArray(660) { index ->
            if (index % 2 == 0) amplitude.toShort() else (-amplitude).toShort()
        }
        return AudioFrame(
            samples = samples,
            length = samples.size,
            sampleRateHz = 22_050,
            startTimeMs = sequence * 30L,
            sequence = sequence
        )
    }
}
