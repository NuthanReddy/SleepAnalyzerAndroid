package tech.future.sleepanalyzer.sleep

import tech.future.sleepanalyzer.data.db.entity.UserProfile
import tech.future.sleepanalyzer.util.Constants

/**
 * Pure scoring logic extracted from [tech.future.sleepanalyzer.service.SleepTrackingService] so
 * we can unit-test it and adjust weights by [UserProfile.activityLevel] without re-running a
 * full Android service.
 *
 * Score is 1..100. Components:
 *  - Duration vs ideal window (7–9 h), adjusted slightly for activity level.
 *  - Interruption count (each interruption costs points).
 *  - Deep-sleep ratio vs personalized target.
 *  - Consistency placeholder (50) — separate workstream to compute this from session history.
 */
object SleepQualityScorer {

    /**
     * Expected deep-sleep ratio for a given activity level. Athletes consolidate more deep sleep;
     * sedentary users typically have less.
     */
    private fun targetDeepRatio(profile: UserProfile?): Float = when (profile?.activityLevel) {
        "athlete" -> 0.30f
        "active" -> 0.27f
        "moderate" -> 0.23f
        "light" -> 0.20f
        "sedentary" -> 0.18f
        else -> 0.22f
    }

    /** Ideal sleep duration window in minutes for a given activity level. */
    private fun idealDurationRange(profile: UserProfile?): IntRange = when (profile?.activityLevel) {
        "athlete", "active" -> 450..570    // 7.5–9.5 h
        "sedentary" -> 390..510            // 6.5–8.5 h
        else -> 420..540                    // 7–9 h
    }

    fun calculate(
        durationMinutes: Int,
        interruptions: Int,
        deepSleepMinutes: Int,
        lightSleepMinutes: Int,
        remSleepMinutes: Int,
        profile: UserProfile? = null
    ): Int {
        val idealRange = idealDurationRange(profile)

        val durationScore = when {
            durationMinutes in idealRange -> 100
            durationMinutes >= idealRange.first - 60 -> 100 - (idealRange.first - durationMinutes) * 2
            durationMinutes > idealRange.last -> (100 - (durationMinutes - idealRange.last)).coerceAtLeast(0)
            else -> (durationMinutes.toFloat() / idealRange.first * 60f).toInt().coerceIn(0, 100)
        }.coerceIn(0, 100)

        val interruptionScore = (100 - interruptions * 15).coerceIn(0, 100)

        val totalSleep = deepSleepMinutes + lightSleepMinutes + remSleepMinutes
        val deepRatio = if (totalSleep > 0) deepSleepMinutes.toFloat() / totalSleep else 0f
        val target = targetDeepRatio(profile)
        val deepScore = (100f - ((target - deepRatio).coerceAtLeast(0f) / target) * 100f)
            .toInt().coerceIn(0, 100)

        val score = (
            durationScore * Constants.DURATION_WEIGHT +
                interruptionScore * Constants.INTERRUPTION_WEIGHT +
                deepScore * Constants.DEEP_SLEEP_WEIGHT +
                50 * Constants.CONSISTENCY_WEIGHT
            ).toInt()

        return score.coerceIn(1, 100)
    }
}
