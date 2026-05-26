package tech.future.sleepanalyzer.sleep

/**
 * Aggregated microphone-derived features over a recent window (typically 1 minute).
 * Produced by `audio/analysis/MicSleepSignalAggregator` and fed into the sleep stage estimator.
 *
 * Every field is optional / nullable so [MicSleepStageEstimator] can degrade gracefully.
 */
data class MicSleepSignal(
    /** Estimated breaths per minute from the smoothed RMS envelope. Null if too noisy or no breath signal. */
    val breathingRateRpm: Float? = null,
    /** 0..1 inverse of breathing rate spectral peak width; 1 = perfectly regular. */
    val breathingRegularity: Float? = null,
    /** Fraction of the window (0..1) below the adaptive noise floor. */
    val silenceRatio: Float = 0f,
    /** Body-turning bursts per minute (broadband short events). */
    val movementBurstsPerMinute: Float = 0f,
    /** Snore epochs per minute from the existing SpectralClassifier. */
    val snoreEventsPerMinute: Float = 0f,
    /** Sleep-talk / muttering events per minute. */
    val talkEventsPerMinute: Float = 0f,
    /** Cough / loud wake startles per minute. */
    val coughEventsPerMinute: Float = 0f,
    /** Sample count used for the aggregation, used by the estimator to weight confidence. */
    val windowSampleCount: Int = 0
)
