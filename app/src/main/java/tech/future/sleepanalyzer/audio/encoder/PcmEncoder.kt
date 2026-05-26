package tech.future.sleepanalyzer.audio.encoder

/**
 * Strategy interface for encoding raw 16-bit mono PCM samples to a file
 * the user (and the in-app playback view) can replay later.
 */
interface PcmEncoder {
    val outputExtension: String
    /**
     * Encodes [pcm] at [sampleRate] to [outputFile] and returns true on success.
     * Implementations should fail gracefully (return false) if encoding cannot complete.
     */
    fun encode(pcm: ShortArray, sampleRate: Int, outputFile: java.io.File): Boolean
}
