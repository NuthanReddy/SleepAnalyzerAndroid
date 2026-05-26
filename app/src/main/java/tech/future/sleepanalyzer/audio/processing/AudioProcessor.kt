package tech.future.sleepanalyzer.audio.processing

import tech.future.sleepanalyzer.audio.AudioFrame

/**
 * A pluggable step in the audio pipeline. Implementations should be stateless
 * across frames where possible, or use their own thread-confined state.
 *
 * The chain pattern lets us compose: BandPass -> VAD -> FeatureExtraction -> Cropper.
 */
fun interface AudioProcessor {
    /**
     * Process a frame and produce a (possibly transformed) result.
     * Returns null to drop the frame from the chain (e.g. VAD dropping silence).
     */
    fun process(frame: AudioFrame): AudioFrame?
}
