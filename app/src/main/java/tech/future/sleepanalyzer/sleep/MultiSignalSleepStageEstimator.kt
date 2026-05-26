package tech.future.sleepanalyzer.sleep

import tech.future.sleepanalyzer.data.db.entity.UserProfile
import tech.future.sleepanalyzer.data.db.entity.WearableSample
import kotlin.math.sqrt

/**
 * Multi-signal estimator combining motion + HR + HRV (+ respiration when available).
 *
 * Heuristics (replaceable by a model later):
 * - DEEP: low motion + HR near or below sleeping baseline + low HRV (parasympathetic dominance is steady) + slow regular respiration.
 * - REM: low body motion (atonia) but elevated HR with high variability and irregular respiration.
 * - LIGHT: moderate motion, HR near average, moderate HRV.
 * - AWAKE: high motion variance and elevated HR.
 *
 * Gracefully degrades to [MotionOnlySleepStageEstimator] if HR/HRV signals are empty so it can be
 * the default everywhere.
 */
class MultiSignalSleepStageEstimator(
    private val fallback: SleepStageEstimator = MotionOnlySleepStageEstimator()
) : SleepStageEstimator {

    override fun estimate(signals: SleepSignals): SleepStageEstimate {
        if (!signals.hasWearableSignal) return fallback.estimate(signals)

        val cutoff = signals.nowMs - WINDOW_MS
        val recent = signals.recentMotion.filter { it.timestampMs >= cutoff }
        val motionVar = motionVariance(recent)
        val hr = signals.recentHeartRate.filter { it.timestamp >= cutoff }
        val hrv = signals.recentHrv.filter { it.timestamp >= cutoff }
        val resp = signals.recentRespiration.filter { it.timestamp >= cutoff }

        val baseHr = signals.restingHeartRateBpm?.toInt()?.coerceIn(40, 90)
            ?: baselineHr(signals.userProfile)
        val avgHr = hr.map { it.value }.average().toFloatOr(baseHr.toFloat())
        val hrStd = hr.map { it.value.toDouble() }.stdDevOr(0.0).toFloat()
        val avgHrv = hrv.map { it.value }.average().toFloatOr(50f)
        val avgResp = resp.map { it.value }.average().toFloatOr(14f)

        val phase = SleepCyclePredictor.phase(signals.sessionStartMs, signals.nowMs, signals.userProfile?.ageYears)
        val bias = SleepCyclePredictor.typicalStageBias(phase)

        // Score each stage 0..1 based on biomarker fit.
        val deepScore = score(
            (motionVar < 0.15f).toFloat() * 0.4f +
            (avgHr < baseHr - 4).toFloat() * 0.3f +
            (avgHrv < 30f).toFloat() * 0.15f +
            (avgResp < 14f).toFloat() * 0.15f
        )
        val remScore = score(
            (motionVar < 0.20f).toFloat() * 0.3f +
            (avgHr > baseHr).toFloat() * 0.25f +
            (hrStd > 6f).toFloat() * 0.25f +
            (avgResp > 14f).toFloat() * 0.2f
        )
        val awakeScore = score(
            (motionVar > 1.0f).toFloat() * 0.5f +
            (avgHr > baseHr + 6).toFloat() * 0.3f +
            (hrStd > 10f).toFloat() * 0.2f
        )
        val lightScore = (1f - maxOf(deepScore, remScore, awakeScore)).coerceAtLeast(0.2f)

        val scores = listOf(
            SleepStage.DEEP to deepScore,
            SleepStage.REM to remScore,
            SleepStage.AWAKE to awakeScore,
            SleepStage.LIGHT to lightScore
        )
        // Slight nudge from cycle bias to break near-ties.
        val nudged = scores.map { (stage, s) ->
            stage to if (stage == bias) s + 0.05f else s
        }.sortedByDescending { it.second }

        val (best, bestScore) = nudged[0]
        val (second, _) = nudged[1]
        return SleepStageEstimate(
            stage = best,
            confidence = bestScore.coerceIn(0.4f, 0.95f),
            secondGuess = second.takeIf { it != best },
            rationale = "motionVar=${"%.3f".format(motionVar)} hr=${"%.0f".format(avgHr)}/${"%.0f".format(hrStd)} hrv=${"%.0f".format(avgHrv)} resp=${"%.1f".format(avgResp)} phase=${"%.2f".format(phase)}"
        )
    }

    private fun motionVariance(samples: List<MotionSample>): Float {
        if (samples.size < 2) return 0f
        val mean = samples.map { it.magnitude.toDouble() }.average()
        val sq = samples.map { (it.magnitude - mean) * (it.magnitude - mean) }.sum()
        return sqrt(sq / (samples.size - 1)).toFloat()
    }

    private fun baselineHr(profile: UserProfile?): Int {
        val age = profile?.ageYears ?: 30
        val sex = profile?.biologicalSex
        val sexAdj = when (sex) { "female" -> 4; "male" -> -2; else -> 0 }
        val ageAdj = ((age - 30) * 0.2f).toInt()
        return (62 + sexAdj + ageAdj).coerceIn(40, 90)
    }

    private fun Boolean.toFloat(): Float = if (this) 1f else 0f
    private fun score(v: Float): Float = v.coerceIn(0f, 1f)
    private fun Double.toFloatOr(default: Float): Float = if (this.isNaN()) default else this.toFloat()
    private fun List<Double>.stdDevOr(default: Double): Double {
        if (size < 2) return default
        val mean = average()
        return sqrt(sumOf { (it - mean) * (it - mean) } / (size - 1))
    }

    companion object {
        const val WINDOW_MS = 5L * 60 * 1000
    }
}
