package tech.future.sleepanalyzer.sleep

/**
 * Given an [SleepStageEstimate] and the active wake window, decides whether the smart alarm
 * should fire immediately, wait, or defer to the deadline alarm.
 *
 * Decision policy (initial):
 * - FIRE_NOW when stage is AWAKE OR (LIGHT with confidence >= [lightFireConfidence]) AND
 *   at least [minWindowElapsedMs] has elapsed inside the window so we don't fire instantly.
 * - FIRE_AT_DEADLINE when the deadline is within [deadlineSoonMs].
 * - WAIT otherwise.
 */
class SmartWakeAnalyzer(
    private val minWindowElapsedMs: Long = 5L * 60 * 1000,
    private val deadlineSoonMs: Long = 60L * 1000,
    private val lightFireConfidence: Float = 0.6f
) {

    enum class WakeDecision { FIRE_NOW, WAIT, FIRE_AT_DEADLINE }

    fun decide(
        estimate: SleepStageEstimate,
        nowMs: Long,
        windowStartMs: Long,
        windowEndMs: Long
    ): WakeDecision {
        if (nowMs >= windowEndMs - deadlineSoonMs) return WakeDecision.FIRE_AT_DEADLINE
        val elapsed = nowMs - windowStartMs
        if (elapsed < minWindowElapsedMs) return WakeDecision.WAIT

        return when (estimate.stage) {
            SleepStage.AWAKE -> WakeDecision.FIRE_NOW
            SleepStage.LIGHT -> if (estimate.confidence >= lightFireConfidence) WakeDecision.FIRE_NOW else WakeDecision.WAIT
            SleepStage.REM, SleepStage.DEEP -> WakeDecision.WAIT
        }
    }
}
