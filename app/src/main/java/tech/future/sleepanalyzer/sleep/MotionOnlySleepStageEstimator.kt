package tech.future.sleepanalyzer.sleep

import kotlin.math.sqrt

/**
 * Estimator used when no wearable data is available.
 * Inputs: a fused motion signal over the last ~5 minutes + cycle phase (from age-adjusted SleepCyclePredictor).
 *
 * The movement signal fuses several phone sensors (all optional / guarded), so the estimator is more
 * robust than accelerometer-only:
 * - accelerometer magnitude + weighted gyroscope (rotation-only tossing still registers),
 * - step-counter delta -> the sleeper physically got up (decisive AWAKE),
 * - ambient light -> lights-on / phone-handled (AWAKE when paired with movement).
 *
 * Heuristics:
 * - Recent steps, or high fused variance -> AWAKE.
 * - Lights on + some movement, or moderate variance -> LIGHT.
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
        val variance = movementVariance(recent)
        val mean = recent.map { it.magnitude }.average().toFloat()
        val phase = SleepCyclePredictor.phase(signals.sessionStartMs, signals.nowMs, signals.userProfile?.ageYears)
        val bias = SleepCyclePredictor.typicalStageBias(phase)

        // Multi-sensor awake cues (all optional): a step means the sleeper physically got up; a
        // bright or sharply rising ambient-light reading means lights-on / phone handled.
        val steppedRecently = recent.any { it.stepDelta > 0 }
        val lightsOn = ambientSuggestsAwake(recent)

        val stage = when {
            steppedRecently -> SleepStage.AWAKE
            variance > AWAKE_VARIANCE -> SleepStage.AWAKE
            lightsOn && variance > LIGHT_VARIANCE -> SleepStage.AWAKE
            variance > LIGHT_VARIANCE -> SleepStage.LIGHT
            variance > DEEP_VARIANCE && bias == SleepStage.DEEP -> SleepStage.DEEP
            variance <= REM_VARIANCE && bias == SleepStage.REM -> SleepStage.REM
            else -> bias
        }
        val confidence = when (stage) {
            SleepStage.AWAKE ->
                if (steppedRecently) 0.9f
                else (variance / (AWAKE_VARIANCE * 2)).coerceIn(0.5f, 0.95f)
            SleepStage.LIGHT -> 0.55f
            SleepStage.DEEP -> 0.6f
            SleepStage.REM -> 0.5f
        }
        return SleepStageEstimate(
            stage = stage,
            confidence = confidence,
            secondGuess = bias.takeIf { it != stage },
            rationale = "var=${"%.3f".format(variance)} mean=${"%.3f".format(mean)} " +
                "phase=${"%.2f".format(phase)} steps=${if (steppedRecently) 1 else 0} lightsOn=$lightsOn"
        )
    }

    /**
     * Variance of a *fused* movement signal: accelerometer magnitude plus a weighted gyroscope
     * contribution, so rotation-only tossing (which barely moves the accelerometer) still registers.
     * Reduces exactly to accelerometer variance when no gyroscope samples are present.
     */
    private fun movementVariance(samples: List<MotionSample>): Float {
        if (samples.size < 2) return 0f
        val fused = samples.map { (it.magnitude + GYRO_WEIGHT * it.gyroMagnitude).toDouble() }
        val mean = fused.average()
        val sq = fused.sumOf { (it - mean) * (it - mean) }
        return sqrt(sq / (fused.size - 1)).toFloat()
    }

    /**
     * True when ambient light indicates the user is likely awake: either the room is bright, or the
     * light level rose sharply within the window (turning a lamp on / picking up the phone). Returns
     * false when no light-sensor readings are present.
     */
    private fun ambientSuggestsAwake(samples: List<MotionSample>): Boolean {
        val lux = samples.mapNotNull { it.lightLux }
        if (lux.isEmpty()) return false
        val max = lux.max()
        val min = lux.min()
        return max >= BRIGHT_LUX || (max - min) >= LUX_RISE
    }

    companion object {
        const val WINDOW_MS = 5L * 60 * 1000
        const val MIN_SAMPLES = 30
        const val AWAKE_VARIANCE = 1.2f
        const val LIGHT_VARIANCE = 0.45f
        const val DEEP_VARIANCE = 0.15f
        const val REM_VARIANCE = 0.10f
        /** Gyroscope (rad/s) weight when folded into the fused movement magnitude. */
        const val GYRO_WEIGHT = 0.5f
        /** Ambient light (lux) at/above which the room is considered lit -> awake cue. */
        const val BRIGHT_LUX = 30f
        /** Lux rise within the window that flags a lights-on / phone-handled event. */
        const val LUX_RISE = 20f
    }
}
