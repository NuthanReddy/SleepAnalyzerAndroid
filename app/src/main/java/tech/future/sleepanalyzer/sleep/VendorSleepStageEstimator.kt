package tech.future.sleepanalyzer.sleep

/**
 * Estimator that returns the vendor-reported stage from cached SleepSessionRecord segments.
 * Picks the segment containing `signals.nowMs` and maps it directly.
 *
 * Confidence is high (0.92) because the vendor algorithm was validated against PSG.
 * When no segment covers `now`, falls back to [fallback] (typically the heuristic estimator).
 */
class VendorSleepStageEstimator(
    private val fallback: SleepStageEstimator
) : SleepStageEstimator {

    override fun estimate(signals: SleepSignals): SleepStageEstimate {
        val segment = signals.vendorStageSegments.firstOrNull { it.contains(signals.nowMs) }
            ?: return fallback.estimate(signals).let {
                it.copy(rationale = "no vendor segment for now; " + it.rationale)
            }
        return SleepStageEstimate(
            stage = segment.stage,
            confidence = 0.92f,
            secondGuess = null,
            rationale = "vendor segment from ${segment.sourceProvider}/${segment.deviceId ?: "?"}"
        )
    }
}
