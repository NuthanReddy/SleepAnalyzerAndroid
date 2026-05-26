package tech.future.sleepanalyzer.audio.encoder

/**
 * Picks the best encoder available on the device.
 * Prefers AAC/M4A for size; falls back to WAV when codec init fails (handled by encoder itself).
 */
object EncoderFactory {
    enum class Backend { M4A_AAC, WAV }

    @Volatile private var preferred: Backend = Backend.M4A_AAC

    fun setBackend(backend: Backend) { preferred = backend }

    fun create(): PcmEncoder = when (preferred) {
        Backend.M4A_AAC -> PcmToM4aEncoder()
        Backend.WAV -> PcmToWavEncoder()
    }
}
