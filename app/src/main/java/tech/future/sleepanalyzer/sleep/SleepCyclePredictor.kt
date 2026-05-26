package tech.future.sleepanalyzer.sleep

/**
 * Models sleep cycle phase as a value in [0, 1) where 0 is the start of a new cycle
 * (descent into deep sleep) and ~0.7+ is the REM-rich tail.
 *
 * Adult cycles average ~90 minutes but range 80-110 min by age, sex, and individual variance.
 * If a [tech.future.sleepanalyzer.data.db.entity.UserProfile] is available we lengthen/shorten
 * the cycle slightly based on age.
 */
object SleepCyclePredictor {

    fun cycleLengthMinutes(ageYears: Int?): Float {
        val a = ageYears ?: 30
        return when {
            a < 18 -> 80f
            a < 30 -> 90f
            a < 50 -> 92f
            a < 65 -> 95f
            else -> 100f
        }
    }

    /** Returns cycle phase in [0, 1). */
    fun phase(sessionStartMs: Long, nowMs: Long, ageYears: Int? = null): Float {
        val cycle = cycleLengthMinutes(ageYears)
        if (cycle <= 0f) return 0f
        val elapsedMin = ((nowMs - sessionStartMs) / 60_000.0).coerceAtLeast(0.0)
        val frac = (elapsedMin % cycle) / cycle
        return frac.toFloat().coerceIn(0f, 0.9999f)
    }

    /** Returns the typical stage distribution at a given phase (informational, not authoritative). */
    fun typicalStageBias(phase: Float): SleepStage = when {
        phase < 0.20f -> SleepStage.LIGHT      // settling
        phase < 0.55f -> SleepStage.DEEP       // SWS dominates
        phase < 0.75f -> SleepStage.LIGHT      // transition
        else -> SleepStage.REM                 // REM-rich tail of cycle
    }
}
