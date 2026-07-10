package tech.future.sleepanalyzer.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.future.sleepanalyzer.data.db.entity.WearableSample

/**
 * Unit tests for [MultiSignalSleepStageEstimator].
 *
 * Baseline HR is pinned via [SleepSignals.restingHeartRateBpm] (= 60) so the biomarker thresholds
 * are deterministic. Cycle bias is controlled through nowMs with a null profile (92-min cycle);
 * bias only breaks near-ties, so the dominant-score cases below are robust to it.
 */
class MultiSignalSleepStageEstimatorTest {

    private val estimator = MultiSignalSleepStageEstimator()

    private fun hr(nowMs: Long, vararg values: Float) =
        values.map { WearableSample(timestamp = nowMs, metric = "heart_rate", value = it) }

    private fun samples(nowMs: Long, vararg values: Float) =
        values.map { WearableSample(timestamp = nowMs, metric = "generic", value = it) }

    private fun motion(nowMs: Long, count: Int, a: Float, b: Float): List<MotionSample> {
        val cutoff = nowMs - MultiSignalSleepStageEstimator.WINDOW_MS
        val step = MultiSignalSleepStageEstimator.WINDOW_MS / (count + 1)
        return (0 until count).map { i ->
            MotionSample(cutoff + step * (i + 1), if (i % 2 == 0) a else b)
        }
    }

    private fun signals(
        nowMs: Long,
        motion: List<MotionSample> = emptyList(),
        heartRate: List<WearableSample> = emptyList(),
        hrv: List<WearableSample> = emptyList(),
        respiration: List<WearableSample> = emptyList()
    ) = SleepSignals(
        sessionStartMs = 0L,
        nowMs = nowMs,
        recentMotion = motion,
        recentHeartRate = heartRate,
        recentHrv = hrv,
        recentRespiration = respiration,
        restingHeartRateBpm = 60f
    )

    @Test
    fun `degrades to fallback when no wearable signal`() {
        val fallback = SleepStageEstimator {
            SleepStageEstimate(SleepStage.AWAKE, 0.123f, rationale = "FALLBACK")
        }
        val out = MultiSignalSleepStageEstimator(fallback)
            .estimate(signals(1_800_000L, motion = motion(1_800_000L, 40, 0f, 3f)))
        assertEquals(SleepStage.AWAKE, out.stage)
        assertEquals("FALLBACK", out.rationale)
    }

    @Test
    fun `deep biomarkers yield deep`() {
        val now = 1_800_000L
        val out = estimator.estimate(
            signals(now, heartRate = hr(now, 50f, 50f), hrv = samples(now, 20f), respiration = samples(now, 12f))
        )
        assertEquals(SleepStage.DEEP, out.stage)
        assertTrue(out.confidence in 0.4f..0.95f)
        assertNotNull(out.secondGuess)
        assertTrue(out.secondGuess != out.stage)
    }

    @Test
    fun `rem biomarkers yield rem`() {
        val now = 4_800_000L
        val out = estimator.estimate(
            signals(now, heartRate = hr(now, 61f, 71f), hrv = samples(now, 40f), respiration = samples(now, 16f))
        )
        assertEquals(SleepStage.REM, out.stage)
    }

    @Test
    fun `awake biomarkers yield awake`() {
        val now = 1_800_000L
        val out = estimator.estimate(
            signals(now, motion = motion(now, 40, 0f, 3f), heartRate = hr(now, 70f, 90f))
        )
        assertEquals(SleepStage.AWAKE, out.stage)
    }

    @Test
    fun `neutral biomarkers yield light`() {
        val now = 600_000L
        val out = estimator.estimate(signals(now, heartRate = hr(now, 60f, 60f)))
        assertEquals(SleepStage.LIGHT, out.stage)
    }
}
