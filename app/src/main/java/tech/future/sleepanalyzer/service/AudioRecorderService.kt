package tech.future.sleepanalyzer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.future.sleepanalyzer.audio.analysis.MicSleepSignalAggregator
import tech.future.sleepanalyzer.MainActivity
import tech.future.sleepanalyzer.R
import tech.future.sleepanalyzer.audio.Attribution
import tech.future.sleepanalyzer.audio.AttributionResult
import tech.future.sleepanalyzer.audio.AudioDispatchers
import tech.future.sleepanalyzer.audio.AudioEventType
import tech.future.sleepanalyzer.audio.AudioFrame
import tech.future.sleepanalyzer.audio.FeatureVector
import tech.future.sleepanalyzer.audio.classification.AudioClassifier
import tech.future.sleepanalyzer.audio.encoder.PcmEncoder
import tech.future.sleepanalyzer.audio.encoder.PcmToWavEncoder
import tech.future.sleepanalyzer.audio.isolation.VoiceMatcher
import tech.future.sleepanalyzer.audio.pipeline.AudioPipeline
import tech.future.sleepanalyzer.audio.processing.AudioCropper
import tech.future.sleepanalyzer.audio.processing.AudioFeatures
import tech.future.sleepanalyzer.audio.processing.BandPassFilter
import tech.future.sleepanalyzer.audio.processing.VoiceActivityDetector
import tech.future.sleepanalyzer.audio.source.AudioRecordSource
import tech.future.sleepanalyzer.audio.util.ShortRingBuffer
import tech.future.sleepanalyzer.data.db.entity.AudioRecording
import tech.future.sleepanalyzer.data.prefs.AppPreferences
import tech.future.sleepanalyzer.di.ServiceLocator
import tech.future.sleepanalyzer.util.Constants
import tech.future.sleepanalyzer.util.PermissionsUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorderService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + AudioDispatchers.io)

    private val micAggregator = MicSleepSignalAggregator(Constants.AUDIO_SAMPLE_RATE)

    private var wakeLock: PowerManager.WakeLock? = null
    private var pipeline: AudioPipeline? = null
    private var ringBuffer: ShortRingBuffer? = null
    private var collectionJob: Job? = null
    private var rolloverJob: Job? = null
    private var sessionId: Long? = null

    private val stateLock = Any()
    private var voiceStartMs: Long? = null
    private var lastVoiceEndMs: Long? = null
    private var silenceStartMs: Long? = null

    /**
     * Silence gap that commits an in-progress event, sourced from user preferences so nearby
     * bursts (snores/coughs a second or two apart) get clubbed into one clip instead of many tiny
     * chunks. Defaults to [AppPreferences.DEFAULT_EVENT_MERGE_GAP_MS]; updated live while recording.
     */
    @Volatile
    private var silenceCommitGapMs: Long = AppPreferences.DEFAULT_EVENT_MERGE_GAP_MS
    private var mergeGapJob: Job? = null

    /**
     * Signal-only mode (#12): runs the analysis pipeline far enough to feed
     * [MicSleepSignalAggregator] for stage estimation, but never encodes audio files or writes
     * [AudioRecording] rows. Used by the mic-for-staging path so users get mic-based stage
     * estimates without producing a snore/talk review library.
     */
    @Volatile
    private var signalOnly = false

    companion object {
        const val ACTION_START = "start_recording"
        const val ACTION_START_SIGNAL_ONLY = "start_recording_signal_only"
        const val ACTION_STOP = "stop_recording"

        private const val UNKNOWN_CONFIDENCE_THRESHOLD = 0.5f
        private const val MIC_ROLLOVER_INTERVAL_MS = 60_000L

        @Volatile
        private var aggregatorRef: MicSleepSignalAggregator? = null

        var isActive = false
            private set

        fun latestMicSignal(): tech.future.sleepanalyzer.sleep.MicSleepSignal? = aggregatorRef?.latest()
    }

    private data class PendingEvent(
        val voiceStartMs: Long,
        val voiceEndMs: Long,
        val triggerTimeMs: Long
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(signalOnly = false)
            ACTION_START_SIGNAL_ONLY -> startRecording(signalOnly = true)
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording(signalOnly: Boolean) {
        if (isActive) return
        if (!PermissionsUtil.isRecordAudioGranted(this)) {
            stopSelf()
            return
        }

        this.signalOnly = signalOnly
        createNotificationChannel()
        startForegroundRecorder(signalOnly)
        acquireWakeLock()
        resetVoiceState()
        micAggregator.reset()
        isActive = true
        aggregatorRef = micAggregator

        rolloverJob?.cancel()
        rolloverJob = serviceScope.launch {
            while (isActive) {
                delay(MIC_ROLLOVER_INTERVAL_MS)
                if (!isActive) break
                micAggregator.rollover()
            }
        }

        mergeGapJob?.cancel()
        mergeGapJob = serviceScope.launch {
            ServiceLocator.preferences.eventMergeGapMsFlow.collect { gap ->
                silenceCommitGapMs = gap
            }
        }

        collectionJob?.cancel()
        collectionJob = serviceScope.launch {
            try {
                sessionId = ServiceLocator.repository.getActiveSession()?.id

                val source = AudioRecordSource(sampleRate = Constants.AUDIO_SAMPLE_RATE)
                val classifier = ServiceLocator.classifier()
                val voiceMatcher = ServiceLocator.voiceMatcher()
                val encoder = ServiceLocator.encoder()
                val vad = VoiceActivityDetector()
                val builder = AudioPipeline.Builder(source)
                    .withRingBuffer(seconds = Constants.AUDIO_BUFFER_SECONDS)
                    .addProcessor(BandPassFilter(sampleRate = source.sampleRate))
                    .onEachFrame { frame ->
                        observeFrame(
                            frame = frame,
                            vad = vad,
                            classifier = classifier,
                            voiceMatcher = voiceMatcher,
                            encoder = encoder,
                            sampleRate = source.sampleRate
                        )
                    }

                ringBuffer = requireNotNull(builder.ringBuffer())
                pipeline = builder.build()
                pipeline?.frames()?.collect {
                    micAggregator.onFrame(rms = vad.lastRms, isVoice = vad.lastIsVoice)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                shutdownRecorder()
            }
        }
    }

    private fun observeFrame(
        frame: AudioFrame,
        vad: VoiceActivityDetector,
        classifier: AudioClassifier,
        voiceMatcher: VoiceMatcher,
        encoder: PcmEncoder,
        sampleRate: Int
    ) {
        val frameDurationMs = (frame.length.toLong() * 1000L / frame.sampleRateHz).coerceAtLeast(1L)
        val frameEndMs = frame.startTimeMs + frameDurationMs
        val isVoiceFrame = vad.process(frame) != null

        var pendingEvent: PendingEvent? = null

        synchronized(stateLock) {
            if (isVoiceFrame) {
                if (voiceStartMs == null) voiceStartMs = frame.startTimeMs
                lastVoiceEndMs = frameEndMs
                silenceStartMs = null
                return
            }

            val startMs = voiceStartMs ?: return
            val lastMs = lastVoiceEndMs ?: startMs
            if (silenceStartMs == null) silenceStartMs = frame.startTimeMs

            val silenceGapMs = frameEndMs - (silenceStartMs ?: frame.startTimeMs)
            val voiceDurationMs = lastMs - startMs
            if (silenceGapMs < silenceCommitGapMs) return

            voiceStartMs = null
            lastVoiceEndMs = null
            silenceStartMs = null

            if (voiceDurationMs >= Constants.VAD_MIN_VOICE_MS) {
                pendingEvent = PendingEvent(
                    voiceStartMs = startMs,
                    voiceEndMs = lastMs,
                    triggerTimeMs = frameEndMs
                )
            }
        }

        pendingEvent?.let { event ->
            serviceScope.launch {
                commitEvent(
                    event = event,
                    classifier = classifier,
                    voiceMatcher = voiceMatcher,
                    encoder = encoder,
                    sampleRate = sampleRate
                )
            }
        }
    }

    private suspend fun commitEvent(
        event: PendingEvent,
        classifier: AudioClassifier,
        voiceMatcher: VoiceMatcher,
        encoder: PcmEncoder,
        sampleRate: Int
    ) {
        val activeRingBuffer = ringBuffer ?: return
        val snapshotDurationMs = (event.triggerTimeMs - event.voiceStartMs + Constants.VAD_PADDING_MS)
            .coerceAtLeast(Constants.VAD_MIN_VOICE_MS.toLong())
        val snapshotSamples = activeRingBuffer.snapshotLast(
            ((snapshotDurationMs * sampleRate) / 1000L).toInt().coerceAtLeast(sampleRate / 10)
        )
        if (snapshotSamples.isEmpty()) return

        val cropped = AudioCropper.crop(
            samples = snapshotSamples,
            sampleRate = sampleRate,
            paddingMs = Constants.VAD_PADDING_MS
        )
        if (cropped.pcm.isEmpty()) return

        val features = AudioFeatures(sampleRate)
        val featureVec = FeatureVector(bandEnergies = FloatArray(13))
        features.extract(cropped.pcm, 0, cropped.pcm.size, featureVec)

        val classification = classifier.classify(cropped.pcm, 0, cropped.pcm.size, sampleRate, featureVec)
        if (classification.type == AudioEventType.SILENCE) return
        if (classification.type == AudioEventType.UNKNOWN && classification.confidence < UNKNOWN_CONFIDENCE_THRESHOLD) {
            return
        }

        micAggregator.onEvent(classification.type)

        // Signal-only mode (#12): the classification has now fed the staging aggregator; stop here
        // so we never attribute, encode, or persist audio.
        if (signalOnly) return

        val attribution = when (classification.type) {
            AudioEventType.SNORE,
            AudioEventType.TALK -> voiceMatcher.match(featureVec)
            else -> AttributionResult(Attribution.UNKNOWN, 0f)
        }

        val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
            .format(Date(event.voiceStartMs))
        val primaryFile = File(recordingsDir, "$timestamp.${encoder.outputExtension}")

        val savedFile = withContext(AudioDispatchers.io) {
            if (encoder.encode(cropped.pcm, sampleRate, primaryFile)) {
                primaryFile
            } else {
                primaryFile.delete()
                val fallbackFile = File(recordingsDir, "$timestamp.wav")
                if (PcmToWavEncoder().encode(cropped.pcm, sampleRate, fallbackFile)) fallbackFile else null
            }
        } ?: return

        val rawStartTimeMs = event.triggerTimeMs - (snapshotSamples.size * 1000L / sampleRate)
        val startTime = rawStartTimeMs + cropped.startMs
        val endTime = rawStartTimeMs + cropped.endMs
        if (endTime <= startTime) {
            savedFile.delete()
            return
        }

        val durationSeconds = (((endTime - startTime) + 999L) / 1000L).toInt().coerceAtLeast(1)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startTime))

        try {
            ServiceLocator.repository.insertRecording(
                AudioRecording(
                    sessionId = sessionId,
                    filePath = savedFile.absolutePath,
                    startTime = startTime,
                    endTime = endTime,
                    durationSeconds = durationSeconds,
                    type = classification.type.key,
                    attributedTo = attribution.attribution.key,
                    matchConfidence = attribution.confidence,
                    pitchHz = featureVec.pitchHz,
                    croppedFromMs = cropped.startMs,
                    croppedToMs = cropped.endMs,
                    maxAmplitude = featureVec.peak,
                    date = date
                )
            )
        } catch (_: Throwable) {
            savedFile.delete()
        }
    }

    private fun stopRecording() {
        if (!isActive && collectionJob == null && pipeline == null) {
            aggregatorRef = null
            stopSelf()
            return
        }
        isActive = false
        aggregatorRef = null
        pipeline?.stop()
        collectionJob?.cancel()
        collectionJob = null
        rolloverJob?.cancel()
        rolloverJob = null
        mergeGapJob?.cancel()
        mergeGapJob = null
        shutdownRecorder()
    }

    private fun shutdownRecorder() {
        aggregatorRef = null
        signalOnly = false
        rolloverJob?.cancel()
        rolloverJob = null
        mergeGapJob?.cancel()
        mergeGapJob = null
        pipeline?.stop()
        pipeline = null
        ringBuffer?.clear()
        ringBuffer = null
        sessionId = null
        resetVoiceState()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun resetVoiceState() {
        synchronized(stateLock) {
            voiceStartMs = null
            lastVoiceEndMs = null
            silenceStartMs = null
        }
    }

    private fun startForegroundRecorder(signalOnly: Boolean) {
        val text = if (signalOnly) "Analyzing sleep sounds..." else "Recording sleep audio..."
        val notification = createNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.AUDIO_RECORDING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(Constants.AUDIO_RECORDING_NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SleepAnalyzer::AudioRecorder"
        ).apply {
            setReferenceCounted(false)
            if (!isHeld) acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.AUDIO_RECORDING_CHANNEL_ID,
            "Audio Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows while recording sleep audio" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.AUDIO_RECORDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sleep Recorder")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isActive = false
        aggregatorRef = null
        collectionJob?.cancel()
        collectionJob = null
        rolloverJob?.cancel()
        rolloverJob = null
        mergeGapJob?.cancel()
        mergeGapJob = null
        pipeline?.stop()
        pipeline = null
        ringBuffer?.clear()
        ringBuffer = null
        resetVoiceState()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
