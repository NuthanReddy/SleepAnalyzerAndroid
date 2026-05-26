package tech.future.sleepanalyzer.sleep

/**
 * Microphone-only sleep stage estimator. Uses heuristic mapping of breathing rate / regularity,
 * movement burst frequency, silence ratio and snore/talk rates to estimate stage.
 *
 * Designed to be the sole signal in "phone-on-nightstand" mode, but can also serve as a
 * contributor inside [MultiSignalSleepStageEstimator] in the future.
 *
 * Falls back to [fallback] when the mic signal is missing entirely.
 */
class MicSleepStageEstimator(
    private val fallback: SleepStageEstimator = MotionOnlySleepStageEstimator()
) : SleepStageEstimator {

    override fun estimate(signals: SleepSignals): SleepStageEstimate {
        val mic = signals.micSignal ?: return fallback.estimate(signals)
        val phase = SleepCyclePredictor.phase(
            signals.sessionStartMs, signals.nowMs, signals.userProfile?.ageYears
        )
        val bias = SleepCyclePredictor.typicalStageBias(phase)

        val rate = mic.breathingRateRpm
        val regularity = mic.breathingRegularity ?: 0f
        val bursts = mic.movementBurstsPerMinute
        val snore = mic.snoreEventsPerMinute
        val talk = mic.talkEventsPerMinute
        val silence = mic.silenceRatio

        // Score each stage independently in [0,1].
        val awakeScore = (
            (if (bursts > 6f) 1f else bursts / 6f) * 0.45f +
                (if (talk > 1f) 1f else talk) * 0.20f +
                (if (rate == null) 0.4f else (if (rate > 20f || rate < 8f) 1f else 0f)) * 0.15f +
                (if (silence < 0.4f) 1f else 0f) * 0.20f
        ).coerceIn(0f, 1f)

        val deepScore = (
            (if (silence > 0.85f) 1f else silence) * 0.35f +
                (if (rate != null && rate in 11f..16f) 1f else 0f) * 0.30f +
                regularity * 0.25f +
                (if (bursts < 0.5f) 1f else 0f) * 0.10f
        ).coerceIn(0f, 1f)

        val remScore = (
            (if (bursts < 1.5f) 1f else 0f) * 0.25f +
                (if (rate != null && (rate > 16f || rate < 12f)) 1f else 0f) * 0.30f +
                (1f - regularity) * 0.20f +
                (if (talk > 0f) 1f else 0f) * 0.15f +
                (if (snore < 0.5f) 1f else 0f) * 0.10f
        ).coerceIn(0f, 1f)

        val lightScore = (1f - maxOf(deepScore, remScore, awakeScore))
            .coerceAtLeast(0.25f)

        val scores = mutableMapOf(
            SleepStage.DEEP to deepScore,
            SleepStage.REM to remScore,
            SleepStage.AWAKE to awakeScore,
            SleepStage.LIGHT to lightScore
        )
        scores[bias] = (scores[bias] ?: 0f) + 0.05f
        val sorted = scores.entries.sortedByDescending { it.value }
        val best = sorted[0]
        val second = sorted[1]

        val confidence = best.value.coerceIn(0.35f, 0.85f) *
            (if (mic.windowSampleCount >= 30) 1f else 0.6f)

        return SleepStageEstimate(
            stage = best.key,
            confidence = confidence.coerceIn(0.3f, 0.85f),
            secondGuess = second.key.takeIf { it != best.key },
            rationale = "mic rate=${rate?.let { "%.1f".format(it) } ?: "?"} reg=${"%.2f".format(regularity)} " +
                "bursts=${"%.2f".format(bursts)} snore=${"%.2f".format(snore)} silence=${"%.2f".format(silence)}"
        )
    }
}
