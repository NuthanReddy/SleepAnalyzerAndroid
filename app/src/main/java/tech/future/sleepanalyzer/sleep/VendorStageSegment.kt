package tech.future.sleepanalyzer.sleep

/**
 * One stage segment as reported by a wearable vendor (Apple/Fitbit/Samsung/Oura/Garmin/Whoop)
 * via Health Connect's SleepSessionRecord. Multiple segments compose a full session.
 *
 * The interval is half-open [startMs, endMs). Stage uses our own [SleepStage] enum so
 * downstream consumers don't depend on Health Connect types.
 */
data class VendorStageSegment(
    val startMs: Long,
    val endMs: Long,
    val stage: SleepStage,
    val sourceProvider: String = "health_connect",
    val deviceId: String? = null
) {
    fun contains(timestampMs: Long): Boolean = timestampMs in startMs until endMs
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}
