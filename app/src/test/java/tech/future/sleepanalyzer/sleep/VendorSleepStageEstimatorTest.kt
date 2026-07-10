package tech.future.sleepanalyzer.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [VendorSleepStageEstimator]. Segments are half-open [startMs, endMs); the estimator
 * returns the covering segment's stage at high confidence, otherwise delegates to the fallback.
 */
class VendorSleepStageEstimatorTest {

    /** Fallback with a recognizable output so delegation is observable. */
    private val fallback = SleepStageEstimator {
        SleepStageEstimate(SleepStage.LIGHT, 0.4f, rationale = "heuristic")
    }
    private val estimator = VendorSleepStageEstimator(fallback)

    private fun signals(nowMs: Long, segments: List<VendorStageSegment>) = SleepSignals(
        sessionStartMs = 0L,
        nowMs = nowMs,
        recentMotion = emptyList(),
        vendorStageSegments = segments
    )

    @Test
    fun `returns covering segment stage at high confidence`() {
        val seg = VendorStageSegment(0L, 3_600_000L, SleepStage.DEEP, sourceProvider = "health_connect")
        val out = estimator.estimate(signals(1_000_000L, listOf(seg)))
        assertEquals(SleepStage.DEEP, out.stage)
        assertEquals(0.92f, out.confidence, 1e-4f)
        assertNull(out.secondGuess)
        assertTrue(out.rationale.contains("health_connect"))
    }

    @Test
    fun `half-open interval excludes end and picks next segment`() {
        val first = VendorStageSegment(0L, 100L, SleepStage.DEEP)
        val second = VendorStageSegment(100L, 200L, SleepStage.REM)
        // now == 100 is NOT in the first segment (until 100) but IS in the second.
        assertEquals(SleepStage.REM, estimator.estimate(signals(100L, listOf(first, second))).stage)
    }

    @Test
    fun `falls back when no segment covers now`() {
        val seg = VendorStageSegment(0L, 100L, SleepStage.DEEP)
        val out = estimator.estimate(signals(500L, listOf(seg)))
        assertEquals(SleepStage.LIGHT, out.stage)
        assertTrue(out.rationale.startsWith("no vendor segment for now;"))
    }

    @Test
    fun `falls back when segments empty`() {
        val out = estimator.estimate(signals(500L, emptyList()))
        assertEquals(SleepStage.LIGHT, out.stage)
        assertTrue(out.rationale.contains("heuristic"))
    }
}
