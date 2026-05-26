package tech.future.sleepanalyzer.audio.sound

/** Strategy for filling a PCM buffer with synthesized audio. */
fun interface SoundGenerator {
    /** Writes [bufferSize] mono Int16 samples to [out] starting at index 0. */
    fun next(out: ShortArray, bufferSize: Int)
}
