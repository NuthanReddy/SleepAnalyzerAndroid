package tech.future.sleepanalyzer.sleep

/**
 * Single motion sample fused from the phone's inertial / environment sensors.
 *
 * [magnitude] (the linear-acceleration magnitude) is always present. The remaining fields are
 * optional and default to "absent" so older 2-arg call sites keep compiling and devices missing a
 * given sensor degrade gracefully.
 */
data class MotionSample(
    val timestampMs: Long,
    /** Linear-acceleration magnitude in m/s^2 (gravity removed). */
    val magnitude: Float,
    /**
     * Angular-speed magnitude in rad/s from the gyroscope at (or just before) this sample; 0f when
     * the device has no gyroscope. Captures tossing/turning that rotates the phone without shifting
     * the accelerometer much.
     */
    val gyroMagnitude: Float = 0f,
    /**
     * Ambient light in lux from the light sensor, or null when the device has none. A bright or
     * sharply rising reading is a strong "lights on / phone handled / user awake" cue.
     */
    val lightLux: Float? = null,
    /**
     * Steps counted since the previous sample (TYPE_STEP_COUNTER delta). > 0 means the sleeper
     * physically walked (got up) — a decisive awake signal. 0 when the sensor is absent.
     */
    val stepDelta: Int = 0
)
