package tech.future.sleepanalyzer.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MotionOnlySleepStageEstimator].
 *
 * Cycle bias is made deterministic by leaving [SleepSignals.userProfile] null (cycle = 92 min) and
 * choosing nowMs so [SleepCyclePredictor.typicalStageBias] is known:
 *  - elapsed < 18.4 min  -> LIGHT
 *  - 18.4 .. 50.6 min    -> DEEP
 *  - 50.6 .. 69 min      -> LIGHT
 *  - >= 69 min           -> REM
 */
class MotionOnlySleepStageEstimatorTest {

    private val estimator = MotionOnlySleepStageEstimator()

    private fun signals(nowMs: Long, motion: List<MotionSample>) = SleepSignals(
        sessionStartMs = 0L,
        nowMs = nowMs,
        recentMotion = motion
    )

    /** Builds [count] samples with alternating magnitudes packed into the last-5-minutes window. */
    private fun alternating(nowMs: Long, count: Int, a: Float, b: Float): List<MotionSample> {
        val cutoff = nowMs - MotionOnlySleepStageEstimator.WINDOW_MS
        val step = MotionOnlySleepStageEstimator.WINDOW_MS / (count + 1)
        return (0 until count).map { i ->
            MotionSample(timestampMs = cutoff + step * (i + 1), magnitude = if (i % 2 == 0) a else b)
        }
    }

    private fun constant(nowMs: Long, count: Int, magnitude: Float) =
        alternating(nowMs, count, magnitude, magnitude)

    @Test
    fun `not enough motion data returns low-confidence light`() {
        val out = estimator.estimate(signals(600_000L, constant(600_000L, 5, 0.05f)))
        assertEquals(SleepStage.LIGHT, out.stage)
        assertEquals(0.3f, out.confidence, 1e-4f)
        assertTrue(out.rationale.contains("not enough"))
    }

    @Test
    fun `high variance yields awake`() {
        val nowMs = 600_000L // bias LIGHT, but high variance overrides
        val out = estimator.estimate(signals(nowMs, alternating(nowMs, 40, 0f, 3f)))
        assertEquals(SleepStage.AWAKE, out.stage)
        assertTrue("confidence ${out.confidence}", out.confidence in 0.5f..0.95f)
    }

    @Test
    fun `moderate variance yields light`() {
        val nowMs = 1_800_000L // bias DEEP, but moderate variance short-circuits to LIGHT
        val out = estimator.estimate(signals(nowMs, alternating(nowMs, 40, 0f, 1.2f)))
        assertEquals(SleepStage.LIGHT, out.stage)
    }

    @Test
    fun `low variance in deep phase yields deep`() {
        val nowMs = 1_800_000L // elapsed 30 min -> bias DEEP
        val out = estimator.estimate(signals(nowMs, alternating(nowMs, 40, 0f, 0.5f)))
        assertEquals(SleepStage.DEEP, out.stage)
    }

    @Test
    fun `very low variance in rem phase yields rem`() {
        val nowMs = 4_800_000L // elapsed 80 min -> bias REM
        val out = estimator.estimate(signals(nowMs, constant(nowMs, 40, 0.05f)))
        assertEquals(SleepStage.REM, out.stage)
        assertEquals(0.5f, out.confidence, 1e-4f)
    }

    @Test
    fun `low variance without matching bias falls through to bias`() {
        val nowMs = 600_000L // elapsed 10 min -> bias LIGHT; variance 0 hits the else branch
        val out = estimator.estimate(signals(nowMs, constant(nowMs, 40, 0.05f)))
        assertEquals(SleepStage.LIGHT, out.stage)
        assertNull("secondGuess equals the chosen stage so must be null", out.secondGuess)
    }
}
