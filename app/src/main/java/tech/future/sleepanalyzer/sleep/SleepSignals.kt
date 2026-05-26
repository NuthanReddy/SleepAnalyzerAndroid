package tech.future.sleepanalyzer.sleep

import tech.future.sleepanalyzer.data.db.entity.UserProfile
import tech.future.sleepanalyzer.data.db.entity.WearableSample

/**
 * Bundle of all signals the estimator may consult.
 * All optional lists / fields default to empty so estimators handle missing data gracefully.
 */
data class SleepSignals(
    val sessionStartMs: Long,
    val nowMs: Long,
    val recentMotion: List<MotionSample>,
    val recentHeartRate: List<WearableSample> = emptyList(),
    val recentHrv: List<WearableSample> = emptyList(),
    val recentRespiration: List<WearableSample> = emptyList(),
    val recentSpo2: List<WearableSample> = emptyList(),
    val userProfile: UserProfile? = null,
    /**
     * Stage segments imported from a wearable vendor for the current session window
     * (Apple/Fitbit/Samsung/Oura/Garmin/Whoop via Health Connect).
     * When present, [SleepStageEstimatorFactory] prefers [VendorSleepStageEstimator] over heuristics.
     */
    val vendorStageSegments: List<VendorStageSegment> = emptyList(),
    /** Microphone-derived feature aggregate; present when mic-based staging is enabled. */
    val micSignal: MicSleepSignal? = null,
    /**
     * Latest known resting HR (bpm) for the user. When non-null, multi-signal estimator
     * uses this as the baseline instead of the age/sex-derived guess.
     */
    val restingHeartRateBpm: Float? = null
) {
    val elapsedMinutes: Int get() = ((nowMs - sessionStartMs) / 60_000L).toInt()
    val hasHeartRate: Boolean get() = recentHeartRate.isNotEmpty()
    val hasHrv: Boolean get() = recentHrv.isNotEmpty()
    val hasWearableSignal: Boolean get() = hasHeartRate || hasHrv || recentRespiration.isNotEmpty()
    val hasVendorStages: Boolean get() = vendorStageSegments.isNotEmpty()
    val hasMicSignal: Boolean get() = micSignal != null
}
