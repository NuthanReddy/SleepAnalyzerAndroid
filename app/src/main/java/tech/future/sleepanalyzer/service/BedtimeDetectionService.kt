package tech.future.sleepanalyzer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.MainActivity
import tech.future.sleepanalyzer.R
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.sleep.BedtimeDetection
import tech.future.sleepanalyzer.sleep.BedtimeDetector
import tech.future.sleepanalyzer.sleep.BedtimeSignals
import tech.future.sleepanalyzer.util.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * Opt-in (#11) foreground service that watches for "the user has gone to bed" — screen off + body
 * still, sustained for [BedtimeDetector]'s quiet window — and, when detected, auto-starts a normal
 * [SleepTrackingService] session. All decision logic lives in the pure, unit-tested [BedtimeDetector];
 * this class only feeds it screen state + accelerometer stillness on a slow tick and performs the
 * hand-off, so the Android surface stays thin.
 *
 * Best-effort by design: no wake lock is held, so during Doze the tick may pause and detection can
 * lag. That is an intentional battery trade-off for a service that may run all evening.
 */
class BedtimeDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var repository: SleepRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val detector = BedtimeDetector()

    @Volatile private var screenOn = true
    @Volatile private var maxMagnitudeSinceTick = 0f
    private val gravity = FloatArray(3)
    @Volatile private var handedOff = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> screenOn = true
                Intent.ACTION_SCREEN_OFF -> screenOn = false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        repository = SleepRepository(applicationContext)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        screenOn = pm.isInteractive
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startWatching()
            ACTION_STOP -> stopSelfSafely()
        }
        return START_STICKY
    }

    private fun startWatching() {
        detector.reset()
        handedOff = false
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            Constants.BEDTIME_DETECTION_NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        )

        val motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        motionSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        serviceScope.launch {
            while (!handedOff) {
                delay(TICK_MS)
                if (handedOff) break
                // If the user already started a session by hand, stand down.
                if (SleepTrackingService.isTracking) {
                    stopSelfSafely()
                    break
                }
                val magnitude = maxMagnitudeSinceTick
                maxMagnitudeSinceTick = 0f
                val result = detector.onSignals(
                    BedtimeSignals(
                        timestampMs = System.currentTimeMillis(),
                        screenOn = screenOn,
                        motionMagnitude = magnitude
                    )
                )
                if (result is BedtimeDetection.Triggered) {
                    handedOff = true
                    handOffToTracking()
                    break
                }
            }
        }
    }

    private fun handOffToTracking() {
        serviceScope.launch {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val session = SleepSession(
                startTime = System.currentTimeMillis(),
                isTracking = true,
                date = dateFormat.format(Date())
            )
            val id = runCatching { repository.insertSession(session) }.getOrNull()
            if (id != null && id > 0) {
                runCatching {
                    val trackIntent = Intent(
                        this@BedtimeDetectionService,
                        SleepTrackingService::class.java
                    ).apply {
                        action = SleepTrackingService.ACTION_START
                        putExtra(SleepTrackingService.EXTRA_SESSION_ID, id)
                    }
                    ContextCompat.startForegroundService(this@BedtimeDetectionService, trackIntent)
                }
            }
            stopSelfSafely()
        }
    }

    private fun stopSelfSafely() {
        runCatching { sensorManager.unregisterListener(this) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val magnitude = if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            sqrt(
                event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]
            )
        } else {
            val alpha = 0.8f
            for (i in 0..2) {
                gravity[i] = alpha * gravity[i] + (1 - alpha) * event.values[i]
            }
            val lx = event.values[0] - gravity[0]
            val ly = event.values[1] - gravity[1]
            val lz = event.values[2] - gravity[2]
            sqrt(lx * lx + ly * ly + lz * lz)
        }
        if (magnitude > maxMagnitudeSinceTick) maxMagnitudeSinceTick = magnitude
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.BEDTIME_DETECTION_CHANNEL_ID,
            "Bedtime Detection",
            NotificationManager.IMPORTANCE_MIN
        ).apply { description = "Watches for when you fall asleep to auto-start tracking" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.BEDTIME_DETECTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sleep Analyzer")
            .setContentText("Waiting for bedtime\u2026")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { sensorManager.unregisterListener(this) }
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "bedtime_detect_start"
        const val ACTION_STOP = "bedtime_detect_stop"
        private const val TICK_MS = 30_000L
    }
}
