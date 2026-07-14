package tech.future.sleepanalyzer.audio.classification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.future.sleepanalyzer.audio.AudioEventType

/**
 * Unit tests for the pure YAMNet label mapping + decision logic. The native model itself can't run
 * on the plain JVM, so [YamnetClassifier] delegates every non-inference decision to [YamnetLabels],
 * which is what we exercise here.
 */
class YamnetClassifierTest {

    @Test
    fun `snoring and snort map to SNORE`() {
        assertEquals(AudioEventType.SNORE, YamnetLabels.groupFor(38))
        assertEquals(AudioEventType.SNORE, YamnetLabels.groupFor(41))
    }

    @Test
    fun `cough throat-clearing and sneeze map to COUGH`() {
        assertEquals(AudioEventType.COUGH, YamnetLabels.groupFor(42))
        assertEquals(AudioEventType.COUGH, YamnetLabels.groupFor(43))
        assertEquals(AudioEventType.COUGH, YamnetLabels.groupFor(44))
    }

    @Test
    fun `speech classes map to TALK`() {
        for (i in listOf(0, 1, 2, 3, 5, 12, 65)) {
            assertEquals("index $i", AudioEventType.TALK, YamnetLabels.groupFor(i))
        }
    }

    @Test
    fun `untracked classes do not map`() {
        assertNull(YamnetLabels.groupFor(YamnetLabels.SILENCE_INDEX))
        assertNull(YamnetLabels.groupFor(300))
    }

    @Test
    fun `strongest confident group wins`() {
        val (type, conf) = YamnetLabels.decide(
            groupScores = mapOf(
                AudioEventType.COUGH to 0.71f,
                AudioEventType.TALK to 0.22f
            ),
            silenceScore = 0.05f,
            rms = 1200f,
            peak = 24000
        )
        assertEquals(AudioEventType.COUGH, type)
        assertTrue(conf in 0.7f..0.99f)
    }

    @Test
    fun `weak group scores fall through to silence when quiet and sure`() {
        val (type, _) = YamnetLabels.decide(
            groupScores = mapOf(AudioEventType.SNORE to 0.05f),
            silenceScore = 0.8f,
            rms = 40f,
            peak = 400
        )
        assertEquals(AudioEventType.SILENCE, type)
    }

    @Test
    fun `loud unrecognized audio is NOISE`() {
        val (type, _) = YamnetLabels.decide(
            groupScores = emptyMap(),
            silenceScore = 0.1f,
            rms = 1500f,
            peak = 20000
        )
        assertEquals(AudioEventType.NOISE, type)
    }

    @Test
    fun `quiet unrecognized audio is UNKNOWN`() {
        val (type, conf) = YamnetLabels.decide(
            groupScores = emptyMap(),
            silenceScore = 0.2f,
            rms = 120f,
            peak = 900
        )
        assertEquals(AudioEventType.UNKNOWN, type)
        assertEquals(0.3f, conf, 0.001f)
    }

    @Test
    fun `long clip windows span the complete recording`() {
        val sampleCount = 20 * 16_000
        val windowSize = 15_600
        val starts = YamnetClassifier.selectWindowStarts(
            sampleCount = sampleCount,
            windowSize = windowSize,
            hopSize = windowSize / 2,
            maxWindows = 12
        )

        assertEquals(12, starts.size)
        assertEquals(0, starts.first())
        assertEquals(sampleCount - windowSize, starts.last())
        assertTrue((1 until starts.size).all { index -> starts[index] > starts[index - 1] })
    }

    @Test
    fun `short clip retains normal overlapping windows`() {
        val starts = YamnetClassifier.selectWindowStarts(
            sampleCount = 30_000,
            windowSize = 15_600,
            hopSize = 7_800,
            maxWindows = 12
        )

        assertEquals(listOf(0, 7_800, 14_400), starts.toList())
    }
}
