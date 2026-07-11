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

    override fun process(frame: AudioFrame): AudioFrame? {
        val rms = AudioMath.rms(frame.samples, 0, frame.length)
        val zcr = AudioMath.zeroCrossingRate(frame.samples, 0, frame.length)
        lastRms = rms

        val threshold = if (lastIsVoice) noiseFloor * deactivationFactor else noiseFloor * activationFactor
        val isVoice = rms >= threshold && zcr >= zcrMin

        // Track the noise floor toward the ambient level. In non-voice periods we adapt quickly.
        // Crucially we ALSO adapt (slowly) during "voice" periods: in a room with steady
        // background noise above the baseline floor (a fan, AC, or white-noise machine) the very
        // first frame reads as voice, and without voice-period adaptation the floor would stay
        // pinned at the baseline forever — latching the detector into permanent "voice" so no
        // event ever ends and nothing is ever recorded. The slow rate keeps genuine snores/talk
        // (which sit well above the floor) from being swallowed while still self-calibrating.
        val adaptRate = if (isVoice) VOICE_ADAPT_RATE else SILENCE_ADAPT_RATE
        noiseFloor = noiseFloor * (1f - adaptRate) + rms * adaptRate
        if (noiseFloor < baselineFloor) noiseFloor = baselineFloor

        lastIsVoice = isVoice
        return if (isVoice) frame else null
    }

    private companion object {
        const val SILENCE_ADAPT_RATE = 0.03f
        const val VOICE_ADAPT_RATE = 0.006f
    }
}
