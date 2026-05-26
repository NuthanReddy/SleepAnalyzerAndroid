package tech.future.sleepanalyzer.sleep

/**
 * Strategy for predicting the current sleep stage.
 * Implementations are pure functions over [SleepSignals] so they can run on any thread.
 *
 * Why a strategy: motion-only fallback today, multi-signal default when wearables are present,
 * and TFLite / ONNX models in the future. Callers depend only on this interface.
 */
fun interface SleepStageEstimator {
    fun estimate(signals: SleepSignals): SleepStageEstimate
}
