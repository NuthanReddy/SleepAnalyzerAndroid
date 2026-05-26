package tech.future.sleepanalyzer.audio.pipeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import tech.future.sleepanalyzer.audio.AudioDispatchers
import tech.future.sleepanalyzer.audio.AudioFrame
import tech.future.sleepanalyzer.audio.processing.AudioProcessor
import tech.future.sleepanalyzer.audio.source.AudioSource
import tech.future.sleepanalyzer.audio.util.ShortRingBuffer

/**
 * Orchestrates the audio pipeline:
 *   AudioSource -> [processors in order] -> ringBufferTap -> [terminal collector]
 *
 * The pipeline runs entirely on [AudioDispatchers.processing] for downstream stages,
 * while the [source] keeps reading on the capture thread. Backpressure is handled by Flow's
 * conflation - older frames are dropped if processing falls behind.
 */
class AudioPipeline private constructor(
    private val source: AudioSource,
    private val processors: List<AudioProcessor>,
    private val ringBuffer: ShortRingBuffer?,
    private val frameTap: ((AudioFrame) -> Unit)?
) {

    fun frames(): Flow<AudioFrame> {
        var flow: Flow<AudioFrame> = source.frames()
        if (ringBuffer != null) {
            flow = flow.onEach { ringBuffer.write(it.samples, 0, it.length) }
        }
        if (frameTap != null) {
            flow = flow.onEach(frameTap)
        }
        for (p in processors) {
            flow = flow.map { p.process(it) }.filter { it != null }.map { it!! }
        }
        return flow.flowOn(AudioDispatchers.processing)
    }

    fun stop() = source.stop()

    class Builder(private val source: AudioSource) {
        private val processors = mutableListOf<AudioProcessor>()
        private var ringBuffer: ShortRingBuffer? = null
        private var frameTap: ((AudioFrame) -> Unit)? = null

        fun addProcessor(processor: AudioProcessor) = apply { processors.add(processor) }

        fun withRingBuffer(seconds: Int): Builder = apply {
            ringBuffer = ShortRingBuffer(source.sampleRate * seconds)
        }

        fun ringBuffer(): ShortRingBuffer? = ringBuffer

        fun onEachFrame(block: (AudioFrame) -> Unit): Builder = apply { frameTap = block }

        fun build(): AudioPipeline = AudioPipeline(source, processors.toList(), ringBuffer, frameTap)
    }
}
