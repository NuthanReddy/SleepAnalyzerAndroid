package tech.future.sleepanalyzer.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartWakeAnalyzerTest {

    private val analyzer = SmartWakeAnalyzer(
        minWindowElapsedMs = 5L * 60 * 1000,
        deadlineSoonMs = 60L * 1000,
        lightFireConfidence = 0.6f
    )

    private fun estimate(stage: SleepStage, confidence: Float) =
        SleepStageEstimate(stage = stage, confidence = confidence)

    @Test
    fun `wait when window just started`() {
        val start = 1_000_000L
        val end = start + 30L * 60 * 1000
        val result = analyzer.decide(estimate(SleepStage.LIGHT, 0.9f), start + 30_000, start, end)
        assertEquals(SmartWakeAnalyzer.WakeDecision.WAIT, result)
    }

    @Test
    fun `fire at deadline when window almost over`() {
        val start = 1_000_000L
        val end = start + 30L * 60 * 1000
        val result = analyzer.decide(estimate(SleepStage.DEEP, 0.9f), end - 30_000, start, end)
        assertEquals(SmartWakeAnalyzer.WakeDecision.FIRE_AT_DEADLINE, result)
    }

    @Test
    fun `fire now when awake mid-window`() {
        val start = 1_000_000L
        val end = start + 30L * 60 * 1000
        val result = analyzer.decide(estimate(SleepStage.AWAKE, 0.7f), start + 10L * 60 * 1000, start, end)
        assertEquals(SmartWakeAnalyzer.WakeDecision.FIRE_NOW, result)
    }

    @Test
    fun `fire now when confident light sleep`() {
        val start = 1_000_000L
        val end = start + 30L * 60 * 1000
        val result = analyzer.decide(estimate(SleepStage.LIGHT, 0.7f), start + 10L * 60 * 1000, start, end)
        assertEquals(SmartWakeAnalyzer.WakeDecision.FIRE_NOW, result)
    }

    @Test
    fun `wait when light sleep with low confidence`() {
        val start = 1_000_000L
        val end = start + 30L * 60 * 1000
        val result = analyzer.decide(estimate(SleepStage.LIGHT, 0.4f), start + 10L * 60 * 1000, start, end)
        assertEquals(SmartWakeAnalyzer.WakeDecision.WAIT, result)
    }

    @Test
    fun `wait when deep or REM mid-window`() {
        val start = 1_000_000L
        val end = start + 30L * 60 * 1000
        assertEquals(
            SmartWakeAnalyzer.WakeDecision.WAIT,
            analyzer.decide(estimate(SleepStage.DEEP, 0.9f), start + 10L * 60 * 1000, start, end)
        )
        assertEquals(
            SmartWakeAnalyzer.WakeDecision.WAIT,
            analyzer.decide(estimate(SleepStage.REM, 0.9f), start + 10L * 60 * 1000, start, end)
        )
    }

    @Test
    fun `deadline takes precedence over fire now`() {
        val start = 1_000_000L
        val end = start + 30L * 60 * 1000
        val result = analyzer.decide(estimate(SleepStage.AWAKE, 0.9f), end - 30_000, start, end)
        assertEquals(SmartWakeAnalyzer.WakeDecision.FIRE_AT_DEADLINE, result)
    }

    @Test
    fun `fires after minimum elapsed boundary`() {
        val start = 1_000_000L
        val end = start + 30L * 60 * 1000
        val justOver = start + 5L * 60 * 1000 + 1
        assertTrue(analyzer.decide(estimate(SleepStage.AWAKE, 0.9f), justOver, start, end) ==
            SmartWakeAnalyzer.WakeDecision.FIRE_NOW)
    }
}
