package tech.future.sleepanalyzer.audio.source

import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.audio.AudioFrame

/**
 * Abstracts a microphone (or any PCM producer) so the rest of the pipeline
 * is testable and not tied to AudioRecord.
 *
 * Implementations stream 16-bit mono PCM frames at the negotiated sample rate.
 */
interface AudioSource {
    val sampleRate: Int
    val channelCount: Int
    val isRunning: Boolean

    /** Cold flow; collection starts the source, cancellation stops it. */
    fun frames(): Flow<AudioFrame>

    fun stop()
}
