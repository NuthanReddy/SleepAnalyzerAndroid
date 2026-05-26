package tech.future.sleepanalyzer.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.future.sleepanalyzer.MainActivity
import tech.future.sleepanalyzer.R
import tech.future.sleepanalyzer.alarm.AlarmReceiver
import tech.future.sleepanalyzer.di.ServiceLocator
import tech.future.sleepanalyzer.sleep.MotionMonitor
import tech.future.sleepanalyzer.sleep.SleepSignals
import tech.future.sleepanalyzer.sleep.SleepStageEstimatorFactory
import tech.future.sleepanalyzer.sleep.SmartWakeAnalyzer
import tech.future.sleepanalyzer.util.Constants
import tech.future.sleepanalyzer.wearables.WearableMetric
import java.util.concurrent.atomic.AtomicBoolean

class SmartWakeService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val running = AtomicBoolean(true)
    private val analyzer = SmartWakeAnalyzer()

    private lateinit var alarmManager: AlarmManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var monitorJob: Job? = null
    private var ownsMonitor = false
    /** The user's actual target wake time; threaded into the AlarmPlaybackService so snooze
     *  re-arms from target rather than from "now". See backlog #10. */
    @Volatile private var currentWindowEndMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(applicationContext)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                val windowStartMs = intent.getLongExtra(EXTRA_WINDOW_START, System.currentTimeMillis())
                val windowEndMs = intent.getLongExtra(EXTRA_WINDOW_END, windowStartMs + 30 * 60 * 1000)
                if (alarmId < 0L || windowEndMs <= windowStartMs) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                running.set(true)
                acquireWakeLock()
                startForegroundCompat(createNotification())
                monitorJob?.cancel()
                monitorJob = serviceScope.launch {
                    monitorWindow(alarmId, windowStartMs, windowEndMs)
                }
            }

            ACTION_STOP -> {
                running.set(false)
                serviceScope.launch {
                    stopWindowService()
                }
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun monitorWindow(alarmId: Long, windowStartMs: Long, windowEndMs: Long) {
        currentWindowEndMs = windowEndMs
        val repository = ServiceLocator.repository
        val preferences = ServiceLocator.preferences
        while (running.get()) {
            val nowMs = System.currentTimeMillis()
            if (nowMs >= windowEndMs - DEADLINE_SOON_MS) {
                stopWindowService()
                return
            }

            var recentMotion = MotionMonitor.buffer.snapshotSince(nowMs - SIGNAL_WINDOW_MS)
            if (recentMotion.isEmpty() && MotionMonitor.refCount == 0 && !ownsMonitor) {
                ownsMonitor = MotionMonitor.start(applicationContext)
                recentMotion = MotionMonitor.buffer.snapshotSince(nowMs - SIGNAL_WINDOW_MS)
            }

            val heartRate = repository.getWearableSamplesInRange(
                nowMs - SIGNAL_WINDOW_MS,
                nowMs,
                WearableMetric.HEART_RATE.key
            )
            val hrv = repository.getWearableSamplesInRange(
                nowMs - SIGNAL_WINDOW_MS,
                nowMs,
                WearableMetric.HRV_RMSSD.key
            )
            val respiration = repository.getWearableSamplesInRange(
                nowMs - SIGNAL_WINDOW_MS,
                nowMs,
                WearableMetric.RESPIRATORY_RATE.key
            )
            val sessionStartMs = repository.getActiveSession()?.startTime ?: windowStartMs
            val profile = repository.getUserProfile()
            val alarm = repository.getAlarmById(alarmId)
            val (vendorStages, restingHr) = withContext(Dispatchers.IO) {
                repository.getVendorStageSegmentsForSession(
                    sessionStartMs - 8L * 60 * 60 * 1000,
                    nowMs + 60_000
                ) to repository.getLatestRestingHrBpm()
            }
            if (alarm == null || !alarm.isEnabled) {
                stopWindowService()
                return
            }

            val micSignal = if (preferences.micForStagingEnabledFlow.firstOrNull() == true) {
                AudioRecorderService.latestMicSignal()
            } else {
                null
            }

            val signals = SleepSignals(
                sessionStartMs = sessionStartMs,
                nowMs = nowMs,
                recentMotion = recentMotion,
                recentHeartRate = heartRate,
                recentHrv = hrv,
                recentRespiration = respiration,
                userProfile = profile,
                vendorStageSegments = vendorStages,
                restingHeartRateBpm = restingHr,
                micSignal = micSignal
            )
            val estimator = SleepStageEstimatorFactory.create(signals)
            val estimate = estimator.estimate(signals)
            when (analyzer.decide(estimate, nowMs, windowStartMs, windowEndMs)) {
                SmartWakeAnalyzer.WakeDecision.FIRE_NOW -> {
                    fireAlarm(alarm.id)
                    return
                }

                SmartWakeAnalyzer.WakeDecision.FIRE_AT_DEADLINE -> {
                    stopWindowService()
                    return
                }

                SmartWakeAnalyzer.WakeDecision.WAIT -> {
                    if (nowMs >= windowEndMs - DEADLINE_SOON_MS) {
                        stopWindowService()
                        return
                    }
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
    }

    private suspend fun fireAlarm(alarmId: Long) {
        startForegroundService(
            Intent(this, AlarmPlaybackService::class.java).apply {
                action = AlarmPlaybackService.ACTION_START
                putExtra(AlarmPlaybackService.EXTRA_ALARM_ID, alarmId)
                // SmartWake fires EARLY (somewhere inside the window). Tell the playback service
                // what the user's actual target wake time was so snooze re-arms from the target,
                // not from "now" — otherwise repeated snoozes push past the intended wake-up.
                // See backlog #10.
                putExtra(Constants.EXTRA_ORIGINAL_TARGET_MS, currentWindowEndMs)
            }
        )
        createAlarmNotificationChannel()
        showAlarmNotification(alarmId)
        cancelDeadlineAlarm(alarmId)
        stopWindowService()
    }

    private fun cancelDeadlineAlarm(alarmId: Long) {
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            alarmId.toInt() xor DEADLINE_REQUEST_MASK,
            Intent(this, AlarmReceiver::class.java).apply {
                putExtra(Constants.EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.SLEEP_TRACKING_CHANNEL_ID,
            "Sleep Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows while tracking your sleep"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createAlarmNotificationChannel() {
        val channel = NotificationChannel(
            Constants.ALARM_CHANNEL_ID,
            "Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Sleep Analyzer Alarm"
            setBypassDnd(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            Constants.SMART_WAKE_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.SLEEP_TRACKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Smart wake")
            .setContentText("Watching for the best moment to wake you")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showAlarmNotification(alarmId: Long) {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Constants.EXTRA_NAVIGATE_TO, Constants.NAV_ALARM_RINGING)
            putExtra(Constants.EXTRA_ALARM_ID, alarmId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, Constants.ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Wake Up!")
            .setContentText("Time to start your day")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(Constants.ALARM_NOTIFICATION_ID, notification)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.SMART_WAKE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(Constants.SMART_WAKE_NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SleepAnalyzer::SmartWake"
        ).apply {
            setReferenceCounted(false)
            if (!isHeld) acquire()
        }
    }

    private suspend fun stopWindowService() {
        running.set(false)
        withContext(Dispatchers.Main.immediate) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        running.set(false)
        monitorJob?.cancel()
        if (ownsMonitor) {
            MotionMonitor.stop()
            ownsMonitor = false
        }
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "smart_wake_start"
        const val ACTION_STOP = "smart_wake_stop"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_WINDOW_START = "window_start_ms"
        const val EXTRA_WINDOW_END = "window_end_ms"

        private const val CHECK_INTERVAL_MS = 30_000L
        private const val SIGNAL_WINDOW_MS = 5L * 60 * 1000
        private const val DEADLINE_SOON_MS = 60_000L
        private const val DEADLINE_REQUEST_MASK = 0x7777_DEAD
    }
}
