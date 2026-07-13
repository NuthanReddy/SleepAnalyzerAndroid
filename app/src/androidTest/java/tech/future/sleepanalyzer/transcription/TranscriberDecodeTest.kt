package tech.future.sleepanalyzer.transcription

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tech.future.sleepanalyzer.audio.encoder.PcmToM4aEncoder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Verifies that [VoskTranscriber.readPcm] can recover audio from the recorder's default AAC/M4A
 * container (the format that previously broke transcription with "Not a WAV file") as well as from
 * WAV. Uses the app's real [PcmToM4aEncoder] to produce the M4A, so the whole encode -> decode path
 * runs on the device codecs. No speech model or network needed.
 */
@RunWith(AndroidJUnit4::class)
class TranscriberDecodeTest {

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun decodesM4aBackToPcm() {
        val (pcm, sampleRate) = readWavAsset("cough_test.wav")
        val srcRms = rms(pcm)
        assertTrue("source clip should not be silent", srcRms > 100)

        val m4a = File(appContext.cacheDir, "decode_roundtrip.m4a")
        m4a.delete()
        val encoded = PcmToM4aEncoder().encode(pcm, sampleRate, m4a)
        assertTrue("PcmToM4aEncoder failed to encode", encoded && m4a.length() > 0)

        val (decoded, decodedRate) = VoskTranscriber.readPcm(m4a)
        m4a.delete()

        assertEquals("decoded sample rate", sampleRate, decodedRate)
        assertTrue("decoded clip should carry audio energy (rms=${rms(decoded)})", rms(decoded) > 100)
        // AAC adds priming/padding, but length should be within ~0.3s of the source.
        val tolerance = sampleRate * 0.3
        assertTrue(
            "decoded length ${decoded.size} should be near source ${pcm.size}",
            kotlin.math.abs(decoded.size - pcm.size) < tolerance
        )
    }

    @Test
    fun readsWavDirectly() {
        val wav = File(appContext.cacheDir, "direct.wav")
        testContext.assets.open("cough_test.wav").use { input ->
            wav.outputStream().use { input.copyTo(it) }
        }
        val (pcm, rate) = VoskTranscriber.readPcm(wav)
        wav.delete()
        assertTrue("WAV path returned no samples", pcm.isNotEmpty())
        assertTrue("WAV sample rate looks wrong", rate in 8000..48000)
    }

    private fun rms(pcm: ShortArray): Double {
        if (pcm.isEmpty()) return 0.0
        var sumSq = 0.0
        for (s in pcm) sumSq += (s.toInt() * s.toInt()).toDouble()
        return sqrt(sumSq / pcm.size)
    }

    private fun readWavAsset(name: String): Pair<ShortArray, Int> {
        val bytes = testContext.assets.open(name).use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var sampleRate = 44100
        var dataOffset = -1
        var dataSize = 0
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = bb.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> sampleRate = bb.getInt(body + 4)
                "data" -> { dataOffset = body; dataSize = size }
            }
            if (dataOffset >= 0) break
            pos = body + size + (size and 1)
        }
        val end = minOf(dataOffset + dataSize, bytes.size)
        val sb = ShortArray((end - dataOffset) / 2)
        var si = 0
        var i = dataOffset
        while (i + 1 < end) { sb[si++] = bb.getShort(i); i += 2 }
        return sb.copyOf(si) to sampleRate
    }
}
