package tech.future.sleepanalyzer.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepStageEstimatorFactoryTest {

    private fun signals(
        motion: List<MotionSample> = emptyList(),
        vendor: List<VendorStageSegment> = emptyList(),
        mic: MicSleepSignal? = null
    ) = SleepSignals(
        sessionStartMs = 0L,
        nowMs = 60_000L,
        recentMotion = motion,
        vendorStageSegments = vendor,
        micSignal = mic
    )

    @Test
    fun `vendor stages take priority`() {
        val vendor = listOf(VendorStageSegment(0L, 60L * 60 * 1000, SleepStage.DEEP))
        val estimator = SleepStageEstimatorFactory.create(signals(vendor = vendor))
        assertTrue("expected vendor estimator, got ${estimator::class.simpleName}",
            estimator is VendorSleepStageEstimator)
    }

    @Test
    fun `motion only returns motion estimator`() {
        val estimator = SleepStageEstimatorFactory.create(
            signals(motion = listOf(MotionSample(0L, 0.05f)))
        )
        assertTrue("expected motion estimator, got ${estimator::class.simpleName}",
            estimator is MotionOnlySleepStageEstimator)
    }

    @Test
    fun `mic only returns mic estimator`() {
        val mic = MicSleepSignal(
            breathingRateRpm = 14f,
            breathingRegularity = 0.8f,
            silenceRatio = 0.9f,
            windowSampleCount = 60
        )
        val estimator = SleepStageEstimatorFactory.create(signals(mic = mic))
        assertTrue("expected mic estimator, got ${estimator::class.simpleName}",
            estimator is MicSleepStageEstimator)
    }

    @Test
    fun `motion only estimator handles empty motion`() {
        val estimator = MotionOnlySleepStageEstimator()
        val out = estimator.estimate(signals())
        assertEquals(SleepStage.LIGHT, out.stage)
        assertTrue("confidence ${out.confidence} should be low on no data", out.confidence <= 0.4f)
    }

    @Test
    fun `no signals at all still returns a motion estimator`() {
        val estimator = SleepStageEstimatorFactory.create(signals())
        assertTrue(estimator is MotionOnlySleepStageEstimator)
    }
}
