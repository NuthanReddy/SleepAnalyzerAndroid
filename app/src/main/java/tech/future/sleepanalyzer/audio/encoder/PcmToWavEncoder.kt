package tech.future.sleepanalyzer.audio.encoder

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tiny WAV writer used as a fallback when MediaCodec / MediaMuxer is unavailable.
 * Produces a valid 16-bit PCM .wav file.
 */
class PcmToWavEncoder : PcmEncoder {
    override val outputExtension: String = "wav"

    override fun encode(pcm: ShortArray, sampleRate: Int, outputFile: File): Boolean {
        return try {
            FileOutputStream(outputFile).use { fos ->
                val byteCount = pcm.size * 2
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray())
                header.putInt(36 + byteCount)
                header.put("WAVE".toByteArray())
                header.put("fmt ".toByteArray())
                header.putInt(16)
                header.putShort(1)            // PCM
                header.putShort(1)            // mono
                header.putInt(sampleRate)
                header.putInt(sampleRate * 2) // byteRate
                header.putShort(2)            // blockAlign
                header.putShort(16)           // bitsPerSample
                header.put("data".toByteArray())
                header.putInt(byteCount)
                fos.write(header.array())

                val out = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
                for (s in pcm) out.putShort(s)
                fos.write(out.array())
            }
            true
        } catch (_: Throwable) {
            outputFile.delete()
            false
        }
    }
}
