package tech.future.sleepanalyzer.sleep

/** One observation of the environment used by [BedtimeDetector] (#11). */
data class BedtimeSignals(
    val timestampMs: Long,
    val screenOn: Boolean,
    /** Linear-acceleration magnitude in m/s^2 (gravity removed). Lower = more still. */
    val motionMagnitude: Float,
    /**
     * Optional microphone quietness. `null` when the mic isn't being sampled (mic is then ignored);
     * when present it must be `true` for the tick to count as bedtime-like.
     */
    val micQuiet: Boolean? = null
)

/** Result of feeding one [BedtimeSignals] tick to [BedtimeDetector]. */
sealed interface BedtimeDetection {
    /** Conditions not currently satisfied; any in-progress quiet window has been reset. */
    data object Idle : BedtimeDetection

    /** Conditions satisfied and the quiet window is filling toward the threshold. */
    data class Accumulating(val elapsedMs: Long, val remainingMs: Long) : BedtimeDetection

    /** The quiet window has just been satisfied for the first time; caller should start tracking. */
    data object Triggered : BedtimeDetection
}

/**
 * Pure, clock-injected state machine that decides when the user has (probably) gone to bed:
 * screen off + body still + (optionally) mic quiet, sustained for [quietWindowMinutes].
 *
 * Deliberately conservative — a false positive (starting tracking while the user is merely sitting
 * still with the screen off) is worse than a missed night, so ANY violation resets the window and
 * the full quiet window must accrue again.
 *
 * Not thread-safe; drive it from a single loop. All timing comes from [BedtimeSignals.timestampMs],
 * never the system clock, so it is fully deterministic and unit-testable.
 */
class BedtimeDetector(
    private val quietWindowMinutes: Int = DEFAULT_QUIET_WINDOW_MINUTES,
    private val stillThreshold: Float = DEFAULT_STILL_THRESHOLD
) {
    private var windowStartMs: Long? = null
    private var triggered = false

    private val windowMs: Long get() = quietWindowMinutes.coerceAtLeast(1) * 60_000L

    fun onSignals(signals: BedtimeSignals): BedtimeDetection {
        val bedtimeLike = !signals.screenOn &&
            signals.motionMagnitude <= stillThreshold &&
            (signals.micQuiet ?: true)

        if (!bedtimeLike) {
            reset()
            return BedtimeDetection.Idle
        }

        val start = windowStartMs ?: signals.timestampMs.also { windowStartMs = it }
        val elapsed = (signals.timestampMs - start).coerceAtLeast(0L)

        if (elapsed >= windowMs) {
            if (!triggered) {
                triggered = true
                return BedtimeDetection.Triggered
            }
            return BedtimeDetection.Accumulating(elapsed, 0L)
        }
        return BedtimeDetection.Accumulating(elapsed, windowMs - elapsed)
    }

    /** Clears the quiet window and the fired latch (e.g. after the caller consumes a [BedtimeDetection.Triggered]). */
    fun reset() {
        windowStartMs = null
        triggered = false
    }

    companion object {
        const val DEFAULT_QUIET_WINDOW_MINUTES = 15
        /** Below this linear-accel magnitude (m/s^2) we consider the body "still". */
        const val DEFAULT_STILL_THRESHOLD = 0.35f
    }
}
