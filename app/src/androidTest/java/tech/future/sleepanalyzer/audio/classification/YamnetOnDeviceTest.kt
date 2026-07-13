package tech.future.sleepanalyzer.audio.classification

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import tech.future.sleepanalyzer.audio.AudioEventType
import tech.future.sleepanalyzer.audio.ClassificationResult
import tech.future.sleepanalyzer.audio.FeatureVector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * On-device accuracy check for [YamnetClassifier]. Instead of the flaky "play through the speaker,
 * catch it on the mic" path, this feeds real snore/cough WAV clips straight into the classifier's
 * raw-PCM entry point on the device (where the native TFLite runtime is available) and asserts the
 * labels. Deterministic and independent of volume, mic, or acoustics.
 *
 * The clips live in androidTest/assets and are read through the *test* context; the model itself
 * (yamnet.tflite) is loaded from the app-under-test context.
 */
@RunWith(AndroidJUnit4::class)
class YamnetOnDeviceTest {

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun snoreClipIsClassifiedAsSnore() {
        val result = classifyAsset("snore_test.wav")
        Log.i(TAG, "snore_test.wav -> ${result.type} (${"%.3f".format(result.confidence)})")
        assertEquals(
            "snore clip should be SNORE but was ${result.type} @ ${result.confidence}",
            AudioEventType.SNORE,
            result.type
        )
    }

    @Test
    fun coughClipIsClassifiedAsCough() {
        val result = classifyAsset("cough_test.wav")
        Log.i(TAG, "cough_test.wav -> ${result.type} (${"%.3f".format(result.confidence)})")
        assertEquals(
            "cough clip should be COUGH but was ${result.type} @ ${result.confidence}",
            AudioEventType.COUGH,
            result.type
        )
    }

    private fun classifyAsset(name: String): ClassificationResult {
        val classifier = YamnetClassifier.create(appContext)
        assertNotNull("YAMNet model failed to load from assets", classifier)
        val (pcm, sampleRate) = readWav(name)
        val features = FeatureVector().apply {
            var peakAbs = 0
            var sumSq = 0.0
            for (s in pcm) {
                val a = if (s < 0) -s.toInt() else s.toInt()
                if (a > peakAbs) peakAbs = a
                sumSq += (s.toInt() * s.toInt()).toDouble()
            }
            peak = peakAbs
            rms = sqrt(sumSq / pcm.size.coerceAtLeast(1)).toFloat()
        }
        Log.i(TAG, "$name: ${pcm.size} samples @ ${sampleRate}Hz rms=${features.rms} peak=${features.peak}")
        return classifier!!.classify(pcm, 0, pcm.size, sampleRate, features)
    }

    /** Minimal PCM16 WAV reader that scans chunks for `fmt ` and `data`. */
    private fun readWav(assetName: String): Pair<ShortArray, Int> {
        val bytes = testContext.assets.open(assetName).use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size > 12 && tag(bytes, 0) == "RIFF" && tag(bytes, 8) == "WAVE") {
            "$assetName is not a RIFF/WAVE file"
        }
        var sampleRate = 44100
        var dataOffset = -1
        var dataSize = 0
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = tag(bytes, pos)
            val size = bb.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> sampleRate = bb.getInt(body + 4)
                "data" -> { dataOffset = body; dataSize = size }
            }
            if (dataOffset >= 0) break
            pos = body + size + (size and 1) // chunks are word-aligned
        }
        require(dataOffset >= 0) { "$assetName has no data chunk" }
        val end = minOf(dataOffset + dataSize, bytes.size)
        val sb = ShortArray((end - dataOffset) / 2)
        var si = 0
        var i = dataOffset
        while (i + 1 < end) {
            sb[si++] = bb.getShort(i)
            i += 2
        }
        return sb.copyOf(si) to sampleRate
    }

    private fun tag(b: ByteArray, off: Int): String =
        String(b, off, 4, Charsets.US_ASCII)

    companion object {
        private const val TAG = "YamnetOnDeviceTest"
    }
}
