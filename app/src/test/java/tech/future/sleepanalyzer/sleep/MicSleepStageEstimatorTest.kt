package tech.future.sleepanalyzer.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MicSleepStageEstimator]. Mic features are a single aggregate (no windowing), so
 * only nowMs matters for cycle bias; a null profile keeps the cycle at 92 min. The dominant-score
 * cases below are robust to the small (+0.05) bias nudge.
 */
class MicSleepStageEstimatorTest {

    private val estimator = MicSleepStageEstimator()

    private fun signals(nowMs: Long, mic: MicSleepSignal?) = SleepSignals(
        sessionStartMs = 0L,
        nowMs = nowMs,
        recentMotion = emptyList(),
        micSignal = mic
    )

    @Test
    fun `degrades to fallback when mic signal missing`() {
        val fallback = SleepStageEstimator {
            SleepStageEstimate(SleepStage.DEEP, 0.77f, rationale = "FALLBACK")
        }
        val out = MicSleepStageEstimator(fallback).estimate(signals(1_800_000L, mic = null))
        assertEquals(SleepStage.DEEP, out.stage)
        assertEquals("FALLBACK", out.rationale)
    }

    @Test
    fun `quiet regular breathing yields deep`() {
        val mic = MicSleepSignal(
            breathingRateRpm = 13f,
            breathingRegularity = 1.0f,
            silenceRatio = 0.95f,
            movementBurstsPerMinute = 0f,
            windowSampleCount = 60
        )
        assertEquals(SleepStage.DEEP, estimator.estimate(signals(1_800_000L, mic)).stage)
    }

    @Test
    fun `irregular breathing with sparse movement yields rem`() {
        val mic = MicSleepSignal(
            breathingRateRpm = 18f,
            breathingRegularity = 0.0f,
            silenceRatio = 0.1f,
            movementBurstsPerMinute = 0f,
            snoreEventsPerMinute = 0f,
            talkEventsPerMinute = 0.5f,
            windowSampleCount = 60
        )
        assertEquals(SleepStage.REM, estimator.estimate(signals(4_800_000L, mic)).stage)
    }

    @Test
    fun `frequent bursts and talk yield awake`() {
        val mic = MicSleepSignal(
            breathingRateRpm = 25f,
            breathingRegularity = 0.5f,
            silenceRatio = 0.2f,
            movementBurstsPerMinute = 8f,
            talkEventsPerMinute = 2f,
            windowSampleCount = 60
        )
        assertEquals(SleepStage.AWAKE, estimator.estimate(signals(600_000L, mic)).stage)
    }

    @Test
    fun `ambiguous signal yields light`() {
        val mic = MicSleepSignal(
            breathingRateRpm = null,
            breathingRegularity = 0.5f,
            silenceRatio = 0.5f,
            movementBurstsPerMinute = 3f,
            snoreEventsPerMinute = 1f,
            windowSampleCount = 60
        )
        assertEquals(SleepStage.LIGHT, estimator.estimate(signals(600_000L, mic)).stage)
    }

    @Test
    fun `low sample count reduces confidence`() {
        fun deepMic(count: Int) = MicSleepSignal(
            breathingRateRpm = 13f,
            breathingRegularity = 1.0f,
            silenceRatio = 0.95f,
            movementBurstsPerMinute = 0f,
            windowSampleCount = count
        )
        val strong = estimator.estimate(signals(1_800_000L, deepMic(60))).confidence
        val weak = estimator.estimate(signals(1_800_000L, deepMic(10))).confidence
        assertTrue("weak=$weak should be < strong=$strong", weak < strong)
    }
}
