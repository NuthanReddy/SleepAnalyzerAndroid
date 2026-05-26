package tech.future.sleepanalyzer.audio.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Encodes raw PCM to a small AAC stream inside an MP4/M4A container using MediaCodec + MediaMuxer.
 * Falls back to false on any error so callers can switch to [PcmToWavEncoder].
 */
class PcmToM4aEncoder(
    private val bitRate: Int = 64_000,
    private val timeoutUs: Long = 10_000
) : PcmEncoder {

    override val outputExtension: String = "m4a"

    override fun encode(pcm: ShortArray, sampleRate: Int, outputFile: File): Boolean {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        return try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val pcmBytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                for (s in pcm) putShort(s)
            }.array()

            var inputPos = 0
            var presentationTimeUs = 0L
            var endOfStream = false
            var muxerStarted = false
            var trackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                if (!endOfStream) {
                    val inputBufferId = codec.dequeueInputBuffer(timeoutUs)
                    if (inputBufferId >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferId) ?: continue
                        inputBuffer.clear()
                        val remaining = pcmBytes.size - inputPos
                        if (remaining > 0) {
                            val chunk = minOf(remaining, inputBuffer.capacity())
                            inputBuffer.put(pcmBytes, inputPos, chunk)
                            val sampleCount = chunk / 2
                            codec.queueInputBuffer(inputBufferId, 0, chunk, presentationTimeUs, 0)
                            presentationTimeUs += sampleCount.toLong() * 1_000_000L / sampleRate
                            inputPos += chunk
                        } else {
                            codec.queueInputBuffer(inputBufferId, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            endOfStream = true
                        }
                    }
                }

                var outputBufferId = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                while (outputBufferId >= 0 || outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start(); muxerStarted = true
                    } else if (outputBufferId >= 0) {
                        val out = codec.getOutputBuffer(outputBufferId)!!
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            out.position(bufferInfo.offset)
                            out.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, out, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputBufferId, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                    outputBufferId = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                }
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
            true
        } catch (_: Throwable) {
            outputFile.delete()
            false
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { muxer?.stop() } catch (_: Throwable) {}
            try { muxer?.release() } catch (_: Throwable) {}
        }
    }
}
