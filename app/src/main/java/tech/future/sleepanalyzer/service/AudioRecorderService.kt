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
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import tech.future.sleepanalyzer.audio.processing.SpectralNoiseReducer
import tech.future.sleepanalyzer.audio.processing.VoiceActivityDetector
import tech.future.sleepanalyzer.audio.source.AudioRecordSource
import tech.future.sleepanalyzer.audio.source.AudioSource
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
    @Volatile private var eventProcessingContext: EventProcessingContext? = null
    @Volatile private var isStopping = false
    @Volatile private var pendingStartSignalOnly: Boolean? = null

    private val stateLock = Any()
    private var voiceStartMs: Long? = null
    private var lastVoiceEndMs: Long? = null
    private var silenceStartMs: Long? = null
    private val eventProcessingMutex = Mutex()
    private val commitJobsLock = Any()
    private val commitJobs = mutableSetOf<Job>()
    // Temporary frame-level diagnostic counter (see observeFrame). Lets us see whether frames flow
    // and what RMS / noise-floor / voice state the VAD actually produces during a session.
    @Volatile private var diagFrameCount = 0L
    // Running maxima since the last periodic dump, so a short cough transient (~7-17 frames) is never
    // missed by the ~1s sampling window.
    @Volatile private var diagWindowMaxPeak = 0
    @Volatile private var diagWindowMaxRms = 0f
    @Volatile private var diagWindowVoiceFrames = 0

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

        private const val MIC_ROLLOVER_INTERVAL_MS = 60_000L

        // Temporary diagnostic tag for triaging why sleep-session sound events (coughs/talks) are
        // not being persisted. Filter logcat with: adb logcat -s SleepAudioDiag
        private const val DIAG = "SleepAudioDiag"

        @Volatile
        private var aggregatorRef: MicSleepSignalAggregator? = null

        var isActive = false
            private set

        fun latestMicSignal(): tech.future.sleepanalyzer.sleep.MicSleepSignal? = aggregatorRef?.latest()
    }

    private data class DetectedEvent(
        val voiceStartMs: Long,
        val voiceEndMs: Long,
        val triggerTimeMs: Long
    )

    private data class CapturedEvent(
        val event: DetectedEvent,
        val pcmSnapshot: ShortArray
    )

    private data class EventProcessingContext(
        val classifier: AudioClassifier,
        val voiceMatcher: VoiceMatcher,
        val encoder: PcmEncoder,
        val sampleRate: Int
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
        if (isStopping) {
            pendingStartSignalOnly = signalOnly
            return
        }
        if (isActive) return
        if (!PermissionsUtil.isRecordAudioGranted(this)) {
            stopSelf()
            return
        }

        this.signalOnly = signalOnly
        isStopping = false
        Log.i(DIAG, "startRecording: signalOnly=$signalOnly (full-capture=${!signalOnly})")
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

                // Feed the RAW mic signal to the ring buffer, VAD, classifier, and event trigger.
                // A cough is a broadband transient that a spectral denoiser suppresses, so denoising
                // here would drop the event or classify it as SILENCE / low-confidence UNKNOWN.
                // Noise reduction is instead applied only to the cropped PCM we save for playback
                // (see commitEvent), so detection stays on the original, un-suppressed signal.
                val source: AudioSource = AudioRecordSource(sampleRate = Constants.AUDIO_SAMPLE_RATE)
                val classifier = ServiceLocator.classifier()
                val voiceMatcher = ServiceLocator.voiceMatcher()
                val encoder = ServiceLocator.encoder()
                val processingContext = EventProcessingContext(
                    classifier = classifier,
                    voiceMatcher = voiceMatcher,
                    encoder = encoder,
                    sampleRate = source.sampleRate
                )
                eventProcessingContext = processingContext
                val vad = VoiceActivityDetector()
                val builder = AudioPipeline.Builder(source)
                    .withRingBuffer(seconds = Constants.AUDIO_BUFFER_SECONDS)
                    .addProcessor(BandPassFilter(sampleRate = source.sampleRate))
                    .onEachFrame { frame ->
                        observeFrame(
                            frame = frame,
                            vad = vad,
                            processingContext = processingContext
                        )
                    }

                ringBuffer = requireNotNull(builder.ringBuffer())
                pipeline = builder.build()
                Log.i(DIAG, "pipeline built; classifier=${classifier::class.simpleName}; collecting frames (sessionId=$sessionId)")
                pipeline?.frames()?.collect {
                    micAggregator.onFrame(rms = vad.lastRms, isVoice = vad.lastIsVoice)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.e(DIAG, "recorder pipeline failed; flushing pending audio before shutdown", t)
                stopRecording()
            }
        }
    }

    private fun observeFrame(
        frame: AudioFrame,
        vad: VoiceActivityDetector,
        processingContext: EventProcessingContext
    ) {
        val frameDurationMs = (frame.length.toLong() * 1000L / frame.sampleRateHz).coerceAtLeast(1L)
        val frameEndMs = frame.startTimeMs + frameDurationMs
        val isVoiceFrame = vad.process(frame) != null

        // Temporary extensive instrumentation: track per-frame maxima and dump a rich sample ~once/sec
        // so we can see whether audio flows, the real signal levels (peak/rms), the VAD threshold it is
        // compared against, ZCR, and how many frames in the window counted as voice. This distinguishes
        // "levels too low" from "VAD latched on" from "mic returning silence". Remove once confirmed.
        var framePeak = 0
        for (i in 0 until frame.length) {
            val a = kotlin.math.abs(frame.samples[i].toInt())
            if (a > framePeak) framePeak = a
        }
        diagFrameCount++
        if (framePeak > diagWindowMaxPeak) diagWindowMaxPeak = framePeak
        if (vad.lastRms > diagWindowMaxRms) diagWindowMaxRms = vad.lastRms
        if (isVoiceFrame) diagWindowVoiceFrames++
        if (diagFrameCount % 33 == 0L) {
            Log.i(
                DIAG,
                "frames#$diagFrameCount win: maxPeak=$diagWindowMaxPeak maxRms=${"%.1f".format(diagWindowMaxRms)} " +
                    "voiceFrames=$diagWindowVoiceFrames/33 | now rms=${"%.1f".format(vad.lastRms)} peak=$framePeak " +
                    "zcr=${"%.3f".format(vad.lastZcr)} thr=${"%.1f".format(vad.lastThreshold)} " +
                    "floor=${"%.1f".format(vad.noiseFloor)} isVoice=$isVoiceFrame"
            )
            diagWindowMaxPeak = 0
            diagWindowMaxRms = 0f
            diagWindowVoiceFrames = 0
        }

        var detectedEvent: DetectedEvent? = null
        var diagCommitDurMs = -1L

        synchronized(stateLock) {
            if (isVoiceFrame) {
                if (voiceStartMs == null) {
                    voiceStartMs = frame.startTimeMs
                    Log.i(
                        DIAG,
                        "VOICE_START (rms=${"%.1f".format(vad.lastRms)} peak=$framePeak " +
                            "thr=${"%.1f".format(vad.lastThreshold)} zcr=${"%.3f".format(vad.lastZcr)} " +
                            "floor=${"%.1f".format(vad.noiseFloor)})"
                    )
                }
                lastVoiceEndMs = frameEndMs
                silenceStartMs = null
                return
            }

            val startMs = voiceStartMs ?: return
            val lastMs = lastVoiceEndMs ?: startMs
            if (silenceStartMs == null) {
                silenceStartMs = frame.startTimeMs
                Log.i(
                    DIAG,
                    "SILENCE_START after voiceDur=${lastMs - startMs}ms (need ${silenceCommitGapMs}ms gap to commit)"
                )
            }

            val silenceGapMs = frameEndMs - (silenceStartMs ?: frame.startTimeMs)
            val voiceDurationMs = lastMs - startMs
            if (silenceGapMs < silenceCommitGapMs) return

            voiceStartMs = null
            lastVoiceEndMs = null
            silenceStartMs = null
            diagCommitDurMs = voiceDurationMs

            if (voiceDurationMs >= Constants.VAD_MIN_VOICE_MS) {
                detectedEvent = DetectedEvent(
                    voiceStartMs = startMs,
                    voiceEndMs = lastMs,
                    triggerTimeMs = frameEndMs
                )
            }
        }

        if (diagCommitDurMs >= 0) {
            Log.i(
                DIAG,
                "VAD event candidate: voiceDurMs=$diagCommitDurMs min=${Constants.VAD_MIN_VOICE_MS} " +
                    "commit=${detectedEvent != null} (rms=${vad.lastRms} noiseFloor=${vad.noiseFloor})"
            )
        }

        detectedEvent?.let { event ->
            enqueueEvent(event, processingContext)
        }
    }

    private fun enqueueEvent(event: DetectedEvent, processingContext: EventProcessingContext) {
        val capturedEvent = captureEvent(event, processingContext.sampleRate) ?: return
        lateinit var job: Job
        job = serviceScope.launch(start = CoroutineStart.LAZY) {
            eventProcessingMutex.withLock {
                commitEvent(capturedEvent, processingContext)
            }
        }
        synchronized(commitJobsLock) { commitJobs += job }
        job.invokeOnCompletion {
            synchronized(commitJobsLock) { commitJobs -= job }
        }
        job.start()
    }

    private suspend fun flushPendingEvent() {
        val event = synchronized(stateLock) {
            val startMs = voiceStartMs ?: return@synchronized null
            val endMs = lastVoiceEndMs ?: startMs
            voiceStartMs = null
            lastVoiceEndMs = null
            silenceStartMs = null
            if (endMs - startMs >= Constants.VAD_MIN_VOICE_MS) {
                DetectedEvent(
                    voiceStartMs = startMs,
                    voiceEndMs = endMs,
                    triggerTimeMs = System.currentTimeMillis()
                )
            } else {
                null
            }
        }
        val context = eventProcessingContext
        if (event != null && context != null) {
            Log.i(DIAG, "Force-committing pending event while recorder stops")
            captureEvent(event, context.sampleRate)?.let { capturedEvent ->
                eventProcessingMutex.withLock {
                    commitEvent(capturedEvent, context)
                }
            }
        }
        awaitCommitJobs()
    }

    private fun captureEvent(event: DetectedEvent, sampleRate: Int): CapturedEvent? {
        val activeRingBuffer = ringBuffer ?: return null
        val snapshotDurationMs =
            (event.triggerTimeMs - event.voiceStartMs + Constants.VAD_PADDING_MS)
                .coerceAtLeast(Constants.VAD_MIN_VOICE_MS.toLong())
        val snapshotSamples = activeRingBuffer.snapshotLast(
            ((snapshotDurationMs * sampleRate) / 1000L)
                .toInt()
                .coerceAtLeast(sampleRate / 10)
        )
        if (snapshotSamples.isEmpty()) {
            Log.i(DIAG, "DROP: ring-buffer snapshot empty")
            return null
        }
        return CapturedEvent(event = event, pcmSnapshot = snapshotSamples)
    }

    private suspend fun awaitCommitJobs() {
        while (true) {
            val jobs = synchronized(commitJobsLock) { commitJobs.toList() }
            if (jobs.isEmpty()) return
            jobs.joinAll()
        }
    }

    private suspend fun commitEvent(
        capturedEvent: CapturedEvent,
        processingContext: EventProcessingContext
    ) {
        val event = capturedEvent.event
        val classifier = processingContext.classifier
        val voiceMatcher = processingContext.voiceMatcher
        val encoder = processingContext.encoder
        val sampleRate = processingContext.sampleRate
        val snapshotSamples = capturedEvent.pcmSnapshot

        val cropped = AudioCropper.cropActiveSpan(
            samples = snapshotSamples,
            sampleRate = sampleRate,
            paddingMs = Constants.VAD_PADDING_MS
        )
        if (cropped.pcm.isEmpty()) {
            Log.i(DIAG, "DROP: cropped PCM empty (AudioCropper trimmed everything)")
            return
        }

        val features = AudioFeatures(sampleRate)
        val featureVec = FeatureVector(bandEnergies = FloatArray(13))
        features.extract(cropped.pcm, 0, cropped.pcm.size, featureVec)

        val classification = classifier.classify(cropped.pcm, 0, cropped.pcm.size, sampleRate, featureVec)
        Log.i(
            DIAG,
            "commitEvent classified: type=${classification.type} conf=${classification.confidence} " +
                "rms=${featureVec.rms} peak=${featureVec.peak} pcmSamples=${cropped.pcm.size}"
        )
        if (classification.type == AudioEventType.SILENCE) {
            Log.i(DIAG, "DROP: classified SILENCE")
            return
        }
        // VAD already confirmed a sustained (>= VAD_MIN_VOICE_MS) acoustic event upstream, so the
        // classifier's role here is to LABEL the event, not to re-gate it. Quiet bedroom coughs and
        // movements routinely fall below every classifier's loudness/model thresholds (e.g. a real
        // cough peaking at only ~7% full-scale) and come back UNKNOWN. Dropping those left the
        // "Sounds & voices" list empty even though a genuine sound occurred. Keep every VAD-confirmed
        // non-silence event as a reviewable clip; when the model can't label it, persist it as NOISE
        // so nothing VAD caught is silently discarded.
        val eventType = if (classification.type == AudioEventType.UNKNOWN) {
            AudioEventType.NOISE
        } else {
            classification.type
        }

        micAggregator.onEvent(eventType)

        // Signal-only mode (#12): the classification has now fed the staging aggregator; stop here
        // so we never attribute, encode, or persist audio.
        if (signalOnly) {
            Log.i(DIAG, "DROP: signalOnly mode (staging only, not persisting)")
            return
        }

        val attribution = when (eventType) {
            AudioEventType.SNORE,
            AudioEventType.TALK -> voiceMatcher.match(featureVec)
            else -> AttributionResult(Attribution.UNKNOWN, 0f)
        }

        val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
            .format(Date(event.voiceStartMs))
        val primaryFile = File(recordingsDir, "$timestamp.${encoder.outputExtension}")

        // Classification, VAD, and voice attribution above all ran on the RAW cropped PCM so
        // broadband transients (coughs) still trigger and classify correctly. Noise reduction is
        // applied here, only to the bytes we persist for playback, and only when the user has the
        // pref enabled. A fresh reducer is used per clip so no cross-event state leaks in.
        val noiseReductionEnabled = runCatching {
            ServiceLocator.preferences.noiseReductionEnabledFlow.first()
        }.getOrDefault(true)
        val pcmToSave = if (noiseReductionEnabled) {
            // SpectralNoiseReducer has a fixed algorithmic latency (STFT seed + overlap-add delay),
            // which it reports as latencySamples. For a one-shot pass over a finite clip, right-pad
            // with that many zeros so the reducer flushes the clip's real tail, then drop the same
            // number of leading latency samples and trim back to the original length. This keeps the
            // saved clip full-length and time-aligned: no prepended silence and no clipped cough tail.
            val reducer = SpectralNoiseReducer(sampleRate)
            val latencySamples = reducer.latencySamples
            val originalLength = cropped.pcm.size
            val padded = cropped.pcm.copyOf(originalLength + latencySamples)
            val denoised = reducer.process(padded)
            denoised.copyOfRange(latencySamples, latencySamples + originalLength)
        } else {
            cropped.pcm
        }

        val savedFile = withContext(AudioDispatchers.io) {
            if (encoder.encode(pcmToSave, sampleRate, primaryFile)) {
                primaryFile
            } else {
                primaryFile.delete()
                val fallbackFile = File(recordingsDir, "$timestamp.wav")
                if (PcmToWavEncoder().encode(pcmToSave, sampleRate, fallbackFile)) fallbackFile else null
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
                    type = eventType.key,
                    attributedTo = attribution.attribution.key,
                    matchConfidence = attribution.confidence,
                    pitchHz = featureVec.pitchHz,
                    croppedFromMs = cropped.startMs,
                    croppedToMs = cropped.endMs,
                    maxAmplitude = featureVec.peak,
                    date = date
                )
            )
            Log.i(DIAG, "PERSISTED recording: type=${eventType.key} sessionId=$sessionId file=${savedFile.name}")
        } catch (t: Throwable) {
            Log.e(DIAG, "DROP: insertRecording failed", t)
            savedFile.delete()
        }
    }

    private fun stopRecording() {
        if (isStopping) return
        if (!isActive && collectionJob == null && pipeline == null) {
            aggregatorRef = null
            stopSelf()
            return
        }
        isStopping = true
        isActive = false
        aggregatorRef = null
        pipeline?.stop()
        val activeCollectionJob = collectionJob
        collectionJob = null
        rolloverJob?.cancel()
        rolloverJob = null
        mergeGapJob?.cancel()
        mergeGapJob = null
        serviceScope.launch {
            activeCollectionJob?.cancelAndJoin()
            flushPendingEvent()
            shutdownRecorder()
        }
    }

    private fun shutdownRecorder() {
        val restartSignalOnly = pendingStartSignalOnly
        pendingStartSignalOnly = null
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
        eventProcessingContext = null
        sessionId = null
        resetVoiceState()
        releaseWakeLock()
        isStopping = false
        if (restartSignalOnly != null) {
            startRecording(restartSignalOnly)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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
        eventProcessingContext = null
        pendingStartSignalOnly = null
        synchronized(commitJobsLock) { commitJobs.clear() }
        resetVoiceState()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
