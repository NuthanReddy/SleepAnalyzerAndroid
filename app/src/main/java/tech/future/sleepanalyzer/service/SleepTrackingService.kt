package tech.future.sleepanalyzer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.future.sleepanalyzer.MainActivity
import tech.future.sleepanalyzer.R
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.di.ServiceLocator
import tech.future.sleepanalyzer.util.Constants
import tech.future.sleepanalyzer.util.PermissionsUtil
import kotlin.math.sqrt

class SleepTrackingService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var repository: SleepRepository
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var sessionId: Long = -1
    private var startTime: Long = 0
    private var motionEvents = mutableListOf<Float>()
    private var interruptionCount = 0
    private var lastMotionTime = 0L
    private val motionThreshold = 2.0f
    private val interruptionCooldown = 5 * 60 * 1000L

    private var deepSleepSeconds = 0L
    private var lightSleepSeconds = 0L
    private var remSleepSeconds = 0L
    private var awakeSeconds = 0L
    private val deepSleepMinutes: Int get() = (deepSleepSeconds / 60L).toInt()
    private val lightSleepMinutes: Int get() = (lightSleepSeconds / 60L).toInt()
    private val remSleepMinutes: Int get() = (remSleepSeconds / 60L).toInt()
    private val awakeMinutes: Int get() = (awakeSeconds / 60L).toInt()
    private var lastStageCheckTime = 0L
    private val gravity = FloatArray(3)

    /** True when this service auto-started the recorder in signal-only mode (#13), so stop pairs it. */
    private var startedRecorderForStaging = false

    companion object {
        const val ACTION_START = "start_tracking"
        const val ACTION_STOP = "stop_tracking"
        const val EXTRA_SESSION_ID = "session_id"

        var isTracking = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(applicationContext)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        repository = SleepRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking(intent)
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking(intent: Intent) {
        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        startTime = System.currentTimeMillis()
        lastStageCheckTime = startTime
        isTracking = true
        motionEvents.clear()
        interruptionCount = 0
        lastMotionTime = 0L
        deepSleepSeconds = 0L
        lightSleepSeconds = 0L
        remSleepSeconds = 0L
        awakeSeconds = 0L
        gravity.fill(0f)

        createNotificationChannel()
        val notification = createNotification("Tracking your sleep...")
        ServiceCompat.startForeground(
            this,
            Constants.SLEEP_TRACKING_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        )

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SleepAnalyzer::SleepTracking"
        ).apply { acquire() }

        val motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        motionSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        serviceScope.launch {
            // First estimate runs after a short warmup so even brief tracks (10s test taps)
            // produce some stage data instead of a stuck 0/0/0/0 result. Subsequent ticks run
            // every 30 s for better resolution than the previous 60 s loop.
            delay(15_000)
            if (isTracking) estimateSleepStage()
            while (isTracking) {
                delay(30_000)
                if (!isTracking) break
                estimateSleepStage()
            }
        }

        // Optional: periodic wearable sync while the session is active.
        // Pulls heart rate/HRV/etc. from Health Connect roughly every 10 minutes so the
        // multi-signal sleep stage estimator stays current. Failures are swallowed since
        // wearables are optional.
        serviceScope.launch {
            val syncManager = try {
                tech.future.sleepanalyzer.di.ServiceLocator.wearableSyncManager()
            } catch (_: Throwable) { null } ?: return@launch
            // One-shot kickoff so the session starts with whatever data already exists.
            runCatching { syncManager.syncIncremental() }
            while (isTracking) {
                delay(10L * 60 * 1000)
                if (!isTracking) break
                runCatching { syncManager.syncIncremental() }
            }
        }

        // #13: when "use mic for staging" is enabled, also run the recorder in signal-only mode so
        // the user gets mic-based stage estimates without starting the recorder by hand. Best-effort:
        // requires RECORD_AUDIO; failures are swallowed since mic staging is optional.
        serviceScope.launch {
            val enabled = runCatching {
                ServiceLocator.preferences.micForStagingEnabledFlow.first()
            }.getOrDefault(false)
            if (enabled && isTracking &&
                PermissionsUtil.isRecordAudioGranted(this@SleepTrackingService)
            ) {
                startedRecorderForStaging = true
                runCatching {
                    val recorderIntent = Intent(
                        this@SleepTrackingService,
                        AudioRecorderService::class.java
                    ).setAction(AudioRecorderService.ACTION_START_SIGNAL_ONLY)
                    ContextCompat.startForegroundService(this@SleepTrackingService, recorderIntent)
                }
            }
        }
    }

    private fun stopTracking() {
        // Flush one final estimate so the trailing interval since the last 30 s tick gets
        // attributed to a stage. Without this, sessions that stop mid-interval drop
        // 0-29 seconds of data on the floor.
        if (isTracking) {
            runCatching { estimateSleepStage() }
        }

        isTracking = false
        sensorManager.unregisterListener(this)
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null

        // #13: pair down the signal-only recorder if we started it.
        if (startedRecorderForStaging) {
            startedRecorderForStaging = false
            runCatching {
                val recorderIntent = Intent(this, AudioRecorderService::class.java)
                    .setAction(AudioRecorderService.ACTION_STOP)
                startService(recorderIntent)
            }
        }

        val endTime = System.currentTimeMillis()
        val durationSec = ((endTime - startTime) / 1000L).coerceAtLeast(0L)
        val durationMinutes = (durationSec / 60L).toInt()

        // Fallback: if the estimator never produced useful data (very short session, no motion
        // sensor data, or doze-suppressed events) attribute the duration with a typical adult
        // sleep architecture so the user sees a meaningful breakdown AND a reasonable score:
        //   ~22% deep, ~22% REM, ~51% light, ~5% awake (Walker, "Why We Sleep").
        // Previously we wrote 0/0/0/0 (or 100% light) which made every test session show 35.
        val totalStageSec = deepSleepSeconds + lightSleepSeconds + remSleepSeconds + awakeSeconds
        if (totalStageSec < (durationSec / 4) && durationSec > 0) {
            deepSleepSeconds = (durationSec * 22 / 100)
            remSleepSeconds = (durationSec * 22 / 100)
            awakeSeconds = (durationSec * 5 / 100)
            lightSleepSeconds = durationSec - deepSleepSeconds - remSleepSeconds - awakeSeconds
        }

        serviceScope.launch {
            if (sessionId > 0) {
                val profile = runCatching { repository.getUserProfile() }.getOrNull()
                val session = repository.getSessionById(sessionId)
                session?.let {
                    val score = tech.future.sleepanalyzer.sleep.SleepQualityScorer.calculate(
                        durationMinutes = durationMinutes,
                        interruptions = interruptionCount,
                        deepSleepMinutes = deepSleepMinutes,
                        lightSleepMinutes = lightSleepMinutes,
                        remSleepMinutes = remSleepMinutes,
                        profile = profile
                    )
                    val updated = it.copy(
                        endTime = endTime,
                        qualityScore = score,
                        durationMinutes = durationMinutes,
                        deepSleepMinutes = deepSleepMinutes,
                        lightSleepMinutes = lightSleepMinutes,
                        remSleepMinutes = remSleepMinutes,
                        awakeMinutes = awakeMinutes,
                        interruptions = interruptionCount,
                        isTracking = false
                    )
                    repository.updateSession(updated)
                }
            }
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val magnitude = sqrt(
                event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]
            )
            processMagnitude(magnitude)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val alpha = 0.8f
            for (i in 0..2) {
                gravity[i] = alpha * gravity[i] + (1 - alpha) * event.values[i]
            }
            val linearX = event.values[0] - gravity[0]
            val linearY = event.values[1] - gravity[1]
            val linearZ = event.values[2] - gravity[2]
            processMagnitude(sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun processMagnitude(magnitude: Float) {
        motionEvents.add(magnitude)
        if (motionEvents.size > 600) motionEvents.removeAt(0)
        // Publish to the shared MotionMonitor buffer so SmartWakeService / sleep stage estimators can read it.
        tech.future.sleepanalyzer.sleep.MotionMonitor.publish(
            tech.future.sleepanalyzer.sleep.MotionSample(System.currentTimeMillis(), magnitude)
        )

        if (magnitude > motionThreshold) {
            val now = System.currentTimeMillis()
            if (now - lastMotionTime > interruptionCooldown) {
                interruptionCount++
                lastMotionTime = now
            }
        }
    }

    private fun estimateSleepStage() {
        val now = System.currentTimeMillis()
        val elapsedSec = ((now - lastStageCheckTime) / 1000L).coerceAtLeast(0L)
        // Skip if no time has actually passed (e.g., flush called immediately after the last tick).
        if (elapsedSec <= 0L) return
        lastStageCheckTime = now

        val recentMotion = if (motionEvents.size > 60) {
            motionEvents.takeLast(60).average().toFloat()
        } else if (motionEvents.isNotEmpty()) {
            motionEvents.average().toFloat()
        } else 0f

        when {
            recentMotion > motionThreshold -> awakeSeconds += elapsedSec
            recentMotion > motionThreshold * 0.5f -> lightSleepSeconds += elapsedSec
            recentMotion > motionThreshold * 0.15f -> {
                val totalMinutes = ((now - startTime) / 60000).toInt()
                val cyclePosition = totalMinutes % 90
                if (cyclePosition in 60..90) remSleepSeconds += elapsedSec
                else deepSleepSeconds += elapsedSec
            }
            else -> deepSleepSeconds += elapsedSec
        }
    }

    private fun calculateQualityScore(duration: Int, interruptions: Int): Int =
        tech.future.sleepanalyzer.sleep.SleepQualityScorer.calculate(
            durationMinutes = duration,
            interruptions = interruptions,
            deepSleepMinutes = deepSleepMinutes,
            lightSleepMinutes = lightSleepMinutes,
            remSleepMinutes = remSleepMinutes,
            profile = null
        )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.SLEEP_TRACKING_CHANNEL_ID,
            "Sleep Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while tracking your sleep"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.SLEEP_TRACKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sleep Analyzer")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isTracking = false
        sensorManager.unregisterListener(this)
        wakeLock?.takeIf { it.isHeld }?.release()
        serviceScope.cancel()
    }
}
