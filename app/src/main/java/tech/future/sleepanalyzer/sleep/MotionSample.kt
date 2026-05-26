package tech.future.sleepanalyzer.sleep

/** Single accelerometer-derived motion sample. */
data class MotionSample(
    val timestampMs: Long,
    /** Linear-acceleration magnitude in m/s^2 (gravity removed). */
    val magnitude: Float
)
