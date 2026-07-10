package tech.future.sleepanalyzer.transcription

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LogLevel
import org.vosk.LibVosk
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * Offline speech-to-text for stored "talk" recordings using the Vosk small English model.
 *
 * The ~40 MB acoustic model is downloaded on first use into the app's files directory and cached
 * so subsequent transcriptions (and app restarts) work fully offline. All audio processing happens
 * on-device; nothing is uploaded.
 */
object VoskTranscriber {

    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    private const val MODEL_DIR_NAME = "vosk-model-small-en-us-0.15"
    private const val TARGET_SAMPLE_RATE = 16000f

    /** Thrown when transcription cannot proceed (model download failed, unreadable audio, etc.). */
    class TranscriptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val modelMutex = Mutex()
    @Volatile
    private var model: Model? = null

    init {
        runCatching { LibVosk.setLogLevel(LogLevel.WARNINGS) }
    }

    /** True once the model has been downloaded and unpacked at least once. */
    fun isModelReady(context: Context): Boolean = resolvedModelDir(context)?.exists() == true

    /**
     * Transcribes the WAV file at [filePath]. Returns the recognized text (possibly empty if no
     * speech was detected). Runs on [Dispatchers.IO]. Throws [TranscriptionException] on failure.
     */
    suspend fun transcribe(context: Context, filePath: String): String = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) throw TranscriptionException("Recording file not found")

        val loadedModel = ensureModel(context)
        val (samples, sampleRate) = readWavPcm(file)
        val resampled = if (sampleRate == TARGET_SAMPLE_RATE.toInt()) samples
            else resampleLinear(samples, sampleRate, TARGET_SAMPLE_RATE.toInt())

        val recognizer = Recognizer(loadedModel, TARGET_SAMPLE_RATE)
        try {
            val chunk = 4096
            var offset = 0
            while (offset < resampled.size) {
                val len = minOf(chunk, resampled.size - offset)
                recognizer.acceptWaveForm(resampled.copyOfRange(offset, offset + len), len)
                offset += len
            }
            val json = JSONObject(recognizer.finalResult)
            json.optString("text", "").trim()
        } finally {
            recognizer.close()
        }
    }

    private suspend fun ensureModel(context: Context): Model {
        model?.let { return it }
        return modelMutex.withLock {
            model?.let { return it }
            val dir = resolvedModelDir(context) ?: run {
                downloadAndUnzipModel(context)
                resolvedModelDir(context)
                    ?: throw TranscriptionException("Model unpacked but not found")
            }
            val loaded = try {
                Model(dir.absolutePath)
            } catch (t: Throwable) {
                throw TranscriptionException("Failed to load speech model", t)
            }
            model = loaded
            loaded
        }
    }

    /** Returns the directory containing the unpacked model, or null if not present yet. */
    private fun resolvedModelDir(context: Context): File? {
        val root = File(context.filesDir, "vosk")
        val nested = File(root, MODEL_DIR_NAME)
        // Vosk models must contain a `conf/` folder; validate before trusting the folder.
        return when {
            File(nested, "conf").exists() -> nested
            File(root, "conf").exists() -> root
            else -> null
        }
    }

    private fun downloadAndUnzipModel(context: Context) {
        val root = File(context.filesDir, "vosk").apply { mkdirs() }
        val zipFile = File(root, "model.zip")
        try {
            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            connection.inputStream.use { input ->
                zipFile.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(root, entry.name)
                    if (!outFile.canonicalPath.startsWith(root.canonicalPath)) {
                        throw TranscriptionException("Invalid model archive entry")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> zis.copyTo(out, 64 * 1024) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (t: TranscriptionException) {
            throw t
        } catch (t: Throwable) {
            throw TranscriptionException("Could not download speech model", t)
        } finally {
            zipFile.delete()
        }
    }

    /** Reads 16-bit mono PCM samples and the sample rate from a RIFF/WAV file. */
    private fun readWavPcm(file: File): Pair<ShortArray, Int> {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(12)
            if (raf.read(header) < 12) throw TranscriptionException("Malformed audio file")
            val riff = String(header, 0, 4)
            val wave = String(header, 8, 4)
            if (riff != "RIFF" || wave != "WAVE") throw TranscriptionException("Not a WAV file")

            var sampleRate = 22050
            var dataOffset = -1L
            var dataSize = 0
            val chunkHeader = ByteArray(8)
            while (raf.read(chunkHeader) == 8) {
                val id = String(chunkHeader, 0, 4)
                val size = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(size)
                        raf.read(fmt)
                        sampleRate = ByteBuffer.wrap(fmt, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataSize = size
                        break
                    }
                    else -> raf.seek(raf.filePointer + size + (size and 1))
                }
            }
            if (dataOffset < 0 || dataSize <= 0) throw TranscriptionException("No PCM data in audio file")

            raf.seek(dataOffset)
            val bytes = ByteArray(dataSize)
            val read = raf.read(bytes).coerceAtLeast(0)
            val shorts = ShortArray(read / 2)
            ByteBuffer.wrap(bytes, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return shorts to sampleRate
        }
    }

    /** Simple linear resampler; adequate for speech recognition preprocessing. */
    private fun resampleLinear(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (input.isEmpty() || srcRate == dstRate) return input
        val outLen = (input.size.toLong() * dstRate / srcRate).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        val ratio = srcRate.toDouble() / dstRate
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val idx = srcPos.toInt()
            val frac = srcPos - idx
            val a = input[idx].toInt()
            val b = if (idx + 1 < input.size) input[idx + 1].toInt() else a
            out[i] = (a + (b - a) * frac).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }
}
