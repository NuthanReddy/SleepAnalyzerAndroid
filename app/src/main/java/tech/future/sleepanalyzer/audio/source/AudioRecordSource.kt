package tech.future.sleepanalyzer.audio.source

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import tech.future.sleepanalyzer.audio.AudioDispatchers
import tech.future.sleepanalyzer.audio.AudioFrame
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * AudioRecord-backed [AudioSource].
 * - Runs reads on [AudioDispatchers.capture] (dedicated high-priority thread).
 * - Emits ~30 ms frames so downstream stages stay responsive.
 * - Requires RECORD_AUDIO permission to be granted by the caller.
 */
class AudioRecordSource(
    override val sampleRate: Int,
    private val frameSizeSamples: Int = sampleRate / 30,
    private val audioSource: Int = MediaRecorder.AudioSource.MIC
) : AudioSource {

    override val channelCount: Int = 1
    private val running = AtomicBoolean(false)
    override val isRunning: Boolean get() = running.get()

    @Volatile private var record: AudioRecord? = null

    @SuppressLint("MissingPermission")
    override fun frames(): Flow<AudioFrame> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(frameSizeSamples * 2 * 4)

        val ar = AudioRecord(
            audioSource, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            minBuffer
        )
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }
        record = ar
        running.set(true)
        ar.startRecording()
        Log.i(
            "SleepAudioDiag",
            "AudioRecordSource started: sampleRate=$sampleRate frameSize=$frameSizeSamples minBuffer=$minBuffer " +
                "audioSource=$audioSource recordingState=${ar.recordingState}"
        )

        val seq = AtomicLong(0)
        val buffer = ShortArray(frameSizeSamples)
        var diagReads = 0L
        var diagZeroReads = 0L
        try {
            // currentCoroutineContext().isActive cooperates with structured cancellation so
            // takeWhile / collect can abort cleanly. The previous callbackFlow + trySend
            // implementation kept AudioRecord open indefinitely because the blocking read had no
            // cancellation cooperation, and awaitClose was unreachable while the loop ran.
            while (currentCoroutineContext().isActive &&
                running.get() &&
                ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = ar.read(buffer, 0, buffer.size)
                if (read <= 0) {
                    diagZeroReads++
                    if (diagZeroReads % 30 == 1L) {
                        Log.w("SleepAudioDiag", "AudioRecord.read returned $read (zeroReads=$diagZeroReads) - mic producing no data")
                    }
                    continue
                }
                diagReads++
                if (diagReads == 1L) Log.i("SleepAudioDiag", "AudioRecordSource: first frame read ($read samples)")
                emit(
                    AudioFrame(
                        samples = buffer.copyOf(read),
                        length = read,
                        sampleRateHz = sampleRate,
                        startTimeMs = System.currentTimeMillis(),
                        sequence = seq.incrementAndGet()
                    )
                )
            }
        } finally {
            Log.i(
                "SleepAudioDiag",
                "AudioRecordSource loop ended: totalReads=$diagReads zeroReads=$diagZeroReads " +
                    "active=${currentCoroutineContext().isActive} running=${running.get()}"
            )
            try { ar.stop() } catch (_: Throwable) {}
            try { ar.release() } catch (_: Throwable) {}
            record = null
            running.set(false)
        }
    }.flowOn(AudioDispatchers.capture)

    override fun stop() {
        running.set(false)
        record?.let {
            try { it.stop() } catch (_: Throwable) {}
            try { it.release() } catch (_: Throwable) {}
        }
        record = null
    }
}
