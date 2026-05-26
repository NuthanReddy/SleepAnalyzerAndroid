package tech.future.sleepanalyzer.sleep

import kotlin.math.sqrt

/**
 * Estimator used when no wearable data is available.
 * Inputs: motion variance over the last ~5 minutes + cycle phase (from age-adjusted SleepCyclePredictor).
 *
 * Heuristics:
 * - High motion variance -> AWAKE.
 * - Moderate variance early in session -> LIGHT.
 * - Very low variance + cycle phase aligned with deep window -> DEEP.
 * - Very low body motion + late phase -> REM (atonia + cycle tail).
 */
class MotionOnlySleepStageEstimator : SleepStageEstimator {

    override fun estimate(signals: SleepSignals): SleepStageEstimate {
        val cutoff = signals.nowMs - WINDOW_MS
        val recent = signals.recentMotion.filter { it.timestampMs >= cutoff }
        if (recent.size < MIN_SAMPLES) {
            return SleepStageEstimate(SleepStage.LIGHT, 0.3f, rationale = "not enough motion data")
        }
        val variance = motionVariance(recent)
        val mean = recent.map { it.magnitude }.average().toFloat()
        val phase = SleepCyclePredictor.phase(signals.sessionStartMs, signals.nowMs, signals.userProfile?.ageYears)
        val bias = SleepCyclePredictor.typicalStageBias(phase)

        val stage = when {
            variance > AWAKE_VARIANCE -> SleepStage.AWAKE
            variance > LIGHT_VARIANCE -> SleepStage.LIGHT
            variance > DEEP_VARIANCE && bias == SleepStage.DEEP -> SleepStage.DEEP
            variance <= REM_VARIANCE && bias == SleepStage.REM -> SleepStage.REM
            else -> bias
        }
        val confidence = when (stage) {
            SleepStage.AWAKE -> (variance / (AWAKE_VARIANCE * 2)).coerceIn(0.5f, 0.95f)
            SleepStage.LIGHT -> 0.55f
            SleepStage.DEEP -> 0.6f
            SleepStage.REM -> 0.5f
        }
        return SleepStageEstimate(
            stage = stage,
            confidence = confidence,
            secondGuess = bias.takeIf { it != stage },
            rationale = "var=${"%.3f".format(variance)} mean=${"%.3f".format(mean)} phase=${"%.2f".format(phase)}"
        )
    }

    private fun motionVariance(samples: List<MotionSample>): Float {
        if (samples.size < 2) return 0f
        val mean = samples.map { it.magnitude.toDouble() }.average()
        val sq = samples.map { (it.magnitude - mean) * (it.magnitude - mean) }.sum()
        return sqrt(sq / (samples.size - 1)).toFloat()
    }

    companion object {
        const val WINDOW_MS = 5L * 60 * 1000
        const val MIN_SAMPLES = 30
        const val AWAKE_VARIANCE = 1.2f
        const val LIGHT_VARIANCE = 0.45f
        const val DEEP_VARIANCE = 0.15f
        const val REM_VARIANCE = 0.10f
    }
}
