package tech.future.sleepanalyzer.sleep

/** Sleep stages used for both observation (after tracking) and prediction (during sleep). */
enum class SleepStage(val displayName: String, val key: String) {
    AWAKE("Awake", "awake"),
    LIGHT("Light", "light"),
    DEEP("Deep", "deep"),
    REM("REM", "rem");

    companion object {
        fun fromKey(key: String?): SleepStage? = entries.firstOrNull { it.key == key }
    }
}

/** Output of a [SleepStageEstimator]. Confidence is in [0, 1]. */
data class SleepStageEstimate(
    val stage: SleepStage,
    val confidence: Float,
    /** Optional second-best stage; can help downstream policies (e.g. SmartWake). */
    val secondGuess: SleepStage? = null,
    /** Free-text explanation for debugging / displaying to the user. */
    val rationale: String = ""
)
