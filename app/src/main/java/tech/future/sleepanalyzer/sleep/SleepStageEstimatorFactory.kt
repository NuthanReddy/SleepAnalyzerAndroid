package tech.future.sleepanalyzer.sleep

/**
 * Factory selects the best estimator implementation given the signals available.
 *
 * Priority (best signal first):
 *   1. Vendor stage segments present (Apple/Fitbit/Samsung/Oura/etc. via Health Connect)
 *      -> [VendorSleepStageEstimator] with multi-signal/motion fallback for gaps.
 *   2. Motion + wearable HR/HRV present
 *      -> [MultiSignalSleepStageEstimator] (mic signal also folded in when available).
 *   3. Motion only -> [MotionOnlySleepStageEstimator].
 *   4. Mic only (phone-on-nightstand mode) -> [MicSleepStageEstimator].
 *   5. Nothing useful -> [MotionOnlySleepStageEstimator] returns the low-confidence default.
 *
 * All implementations are cheap to construct so we don't cache them.
 */
object SleepStageEstimatorFactory {
    fun create(signals: SleepSignals): SleepStageEstimator {
        val baseHeuristic: SleepStageEstimator = when {
            signals.hasWearableSignal -> MultiSignalSleepStageEstimator()
            signals.recentMotion.isNotEmpty() && signals.hasMicSignal -> MicSleepStageEstimator(
                fallback = MotionOnlySleepStageEstimator()
            )
            signals.recentMotion.isNotEmpty() -> MotionOnlySleepStageEstimator()
            signals.hasMicSignal -> MicSleepStageEstimator()
            else -> MotionOnlySleepStageEstimator()
        }
        return if (signals.hasVendorStages) VendorSleepStageEstimator(fallback = baseHeuristic)
        else baseHeuristic
    }
}
