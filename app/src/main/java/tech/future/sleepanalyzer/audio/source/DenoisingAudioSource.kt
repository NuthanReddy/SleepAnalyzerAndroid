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
 * NOTE: This source is currently unused. Applying denoise at the source also fed the ring buffer,
 * VAD, and classifier a denoised signal, which suppressed broadband transients like coughs and
 * broke their detection. Detection now runs on the RAW mic signal and noise reduction is applied
 * only to the cropped PCM that gets saved for playback (see AudioRecorderService.commitEvent). This
 * class is retained for reference / potential reuse but is no longer part of the capture pipeline.
 *
 * The reducer keeps a fixed, small latency but preserves frame length, timing, and sequence
 * numbers, so a stream wrapped this way stays aligned with the rest of the pipeline.
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
