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
    private val activationFactor: Float = 1.8f,
    private val deactivationFactor: Float = 1.4f,
    private val zcrMin: Float = 0.01f,
    private val calibrationFrames: Int = DEFAULT_CALIBRATION_FRAMES
) : AudioProcessor {

    @Volatile var noiseFloor: Float = baselineFloor
        private set
    @Volatile var lastIsVoice: Boolean = false
        private set
    @Volatile var lastRms: Float = 0f
        private set
    // Diagnostics: expose the most recent ZCR and the activation/deactivation threshold actually
    // used, so the pipeline can log exactly why a frame did or didn't count as voice.
    @Volatile var lastZcr: Float = 0f
        private set
    @Volatile var lastThreshold: Float = 0f
        private set
    private var processedFrames = 0

    override fun process(frame: AudioFrame): AudioFrame? {
        val rms = AudioMath.rms(frame.samples, 0, frame.length)
        val zcr = AudioMath.zeroCrossingRate(frame.samples, 0, frame.length)
        lastRms = rms
        lastZcr = zcr

        if (processedFrames < calibrationFrames) {
            processedFrames++
            noiseFloor = updateFloor(noiseFloor, rms, CALIBRATION_ADAPT_RATE)
            lastThreshold = noiseFloor * activationFactor
            lastIsVoice = false
            return null
        }

        val threshold = if (lastIsVoice) noiseFloor * deactivationFactor else noiseFloor * activationFactor
        lastThreshold = threshold
        val isVoice = rms >= threshold && zcr >= zcrMin

        // Active speech, snores, and coughs must not train the ambient floor. The initial
        // calibration handles steady room noise; after that only confirmed non-voice frames
        // adjust the floor, so a sustained sound cannot decay into permanent silence.
        if (!isVoice) {
            noiseFloor = updateFloor(noiseFloor, rms, SILENCE_ADAPT_RATE)
        }

        lastIsVoice = isVoice
        return if (isVoice) frame else null
    }

    private fun updateFloor(current: Float, rms: Float, rate: Float): Float =
        (current * (1f - rate) + rms * rate).coerceAtLeast(baselineFloor)

    private companion object {
        const val DEFAULT_CALIBRATION_FRAMES = 30
        const val CALIBRATION_ADAPT_RATE = 0.15f
        const val SILENCE_ADAPT_RATE = 0.03f
    }
}
