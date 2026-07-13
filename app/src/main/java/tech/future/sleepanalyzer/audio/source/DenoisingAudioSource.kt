package tech.future.sleepanalyzer.audio.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import tech.future.sleepanalyzer.audio.AudioFrame
import tech.future.sleepanalyzer.audio.processing.SpectralNoiseReducer

/**
 * Wraps another [AudioSource] and runs every captured frame through a [SpectralNoiseReducer],
 * stripping steady background noise (AC, fan, hum) before anything downstream sees the audio.
 *
 * Applying the clean-up at the source means the ring buffer, the voice-activity detector, the event
 * classifier, and the saved recordings all operate on the denoised signal, so detection accuracy
 * and playback both improve. The reducer keeps a fixed, small latency but preserves frame length,
 * timing, and sequence numbers, so the rest of the pipeline is unaffected.
 */
class DenoisingAudioSource(
    private val delegate: AudioSource,
    private val reducer: SpectralNoiseReducer = SpectralNoiseReducer(delegate.sampleRate)
) : AudioSource {

    override val sampleRate: Int get() = delegate.sampleRate
    override val channelCount: Int get() = delegate.channelCount
    override val isRunning: Boolean get() = delegate.isRunning

    override fun frames(): Flow<AudioFrame> = delegate.frames()
        .onStart { reducer.reset() }
        .map { frame ->
            val cleaned = reducer.process(frame.samples, frame.length)
            frame.copy(samples = cleaned, length = cleaned.size)
        }

    override fun stop() = delegate.stop()
}
