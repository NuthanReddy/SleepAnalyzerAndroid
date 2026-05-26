package tech.future.sleepanalyzer.audio.processing

import tech.future.sleepanalyzer.audio.AudioFrame
import tech.future.sleepanalyzer.audio.util.AudioMath

/**
 * Energy + zero-crossing rate voice activity detector.
 * Adapts its noise floor over time so it works in different bedrooms.
 *
 * Emits the frame unchanged when voice activity is detected; null otherwise.
 * The implementation also exposes [lastIsVoice] so the pipeline can observe state.
 */
class VoiceActivityDetector(
    private val baselineFloor: Float = 200f,
    private val activationFactor: Float = 2.2f,
    private val deactivationFactor: Float = 1.4f,
    private val zcrMin: Float = 0.01f
) : AudioProcessor {

    @Volatile var noiseFloor: Float = baselineFloor
        private set
    @Volatile var lastIsVoice: Boolean = false
        private set
    @Volatile var lastRms: Float = 0f
        private set
    @Volatile var lastZcr: Float = 0f
        private set

    override fun process(frame: AudioFrame): AudioFrame? {
        val rms = AudioMath.rms(frame.samples, 0, frame.length)
        val zcr = AudioMath.zeroCrossingRate(frame.samples, 0, frame.length)
        lastRms = rms
        lastZcr = zcr

        val threshold = if (lastIsVoice) noiseFloor * deactivationFactor else noiseFloor * activationFactor
        val isVoice = rms >= threshold && zcr >= zcrMin

        if (!isVoice) {
            // Smoothly track noise floor in non-voice periods
            noiseFloor = noiseFloor * 0.97f + rms * 0.03f
            if (noiseFloor < baselineFloor) noiseFloor = baselineFloor
        }
        lastIsVoice = isVoice
        return if (isVoice) frame else null
    }
}
