package tech.future.sleepanalyzer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.MainActivity
import tech.future.sleepanalyzer.R
import tech.future.sleepanalyzer.alarm.AlarmScheduler
import tech.future.sleepanalyzer.data.db.entity.AlarmConfig
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.util.Constants

class AlarmPlaybackService : Service() {

    private lateinit var repository: SleepRepository
    private lateinit var scheduler: AlarmScheduler
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentAlarmId: Long = -1L
    /**
     * The user's intended wake time (epoch millis), threaded from AlarmReceiver / SmartWakeService.
     * Used by [snoozeAlarm] so snoozes re-arm from (target + snoozeDuration) instead of (now +
     * snoozeDuration). Without this, repeated snoozes after an early SmartWake fire push the
     * actual wake-up well past the user's target. See backlog #10.
     */
    private var originalTargetMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = SleepRepository(applicationContext)
        scheduler = AlarmScheduler(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, currentAlarmId) ?: currentAlarmId
        val target = intent?.getLongExtra(Constants.EXTRA_ORIGINAL_TARGET_MS, 0L) ?: 0L
        if (target > 0L) originalTargetMs = target
        when (intent?.action) {
            ACTION_START -> {
                if (alarmId < 0L) {
                    stopPlayback()
                    return START_NOT_STICKY
                }
                currentAlarmId = alarmId
                startForegroundCompat(createNotification(alarmId))
                loadAndPlayAlarm(alarmId)
            }
            ACTION_STOP -> stopPlayback()
            ACTION_SNOOZE -> {
                if (alarmId < 0L) {
                    stopPlayback()
                    return START_NOT_STICKY
                }
                snoozeAlarm(alarmId)
            }
            else -> stopPlayback()
        }
        return START_STICKY
    }

    private fun loadAndPlayAlarm(alarmId: Long) {
        serviceScope.launch {
            val alarm = repository.getAlarmById(alarmId)
            if (alarm == null) {
                stopPlayback()
                return@launch
            }

            if (startPlayback(alarm)) {
                updateNotification(alarm)
            }
        }
    }

    private fun startPlayback(alarm: AlarmConfig): Boolean {
        releaseMedia()
        currentAlarmId = alarm.id

        val toneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return false

        return try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmPlaybackService, toneUri)
                isLooping = true
                prepare()
                start()
            }

            if (alarm.isVibrationEnabled) {
                startVibration()
            } else {
                vibrator?.cancel()
            }
            true
        } catch (_: Throwable) {
            stopPlayback()
            false
        }
    }

    private fun snoozeAlarm(alarmId: Long) {
        serviceScope.launch {
            try {
                repository.getAlarmById(alarmId)?.let { alarm ->
                    val snoozeMinutes = alarm.snoozeDurationMinutes.coerceAtLeast(1)
                    val nowMs = System.currentTimeMillis()
                    // Anchor snooze to the user's original target wake time if known. This way a
                    // SmartWake that fires 25 min early followed by two snoozes still finishes
                    // around the target, not 18 min after the alarm we just dismissed.
                    val anchor = if (originalTargetMs > nowMs) originalTargetMs else nowMs
                    val triggerAtMillis = (anchor + snoozeMinutes * 60_000L).coerceAtLeast(nowMs + 60_000L)
                    scheduler.scheduleAt(alarm.copy(wakeWindowMinutes = 0, daysOfWeek = ""), triggerAtMillis)
                }
            } finally {
                stopPlayback()
            }
        }
    }

    private fun updateNotification(alarm: AlarmConfig) {
        getSystemService(NotificationManager::class.java).notify(
            Constants.ALARM_PLAYBACK_NOTIFICATION_ID,
            createNotification(
                alarmId = alarm.id,
                title = alarm.label.ifBlank { "Wake Up!" },
                soundName = alarm.soundName
            )
        )
    }

    private fun createNotification(
        alarmId: Long,
        title: String = "Alarm ringing",
        soundName: String? = null
    ): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Constants.EXTRA_NAVIGATE_TO, Constants.NAV_ALARM_RINGING)
            putExtra(Constants.EXTRA_ALARM_ID, alarmId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            alarmId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, AlarmPlaybackService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val snoozeIntent = Intent(this, AlarmPlaybackService::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_ALARM_ID, alarmId)
            // Preserve the original target so snooze re-arms from target, not from "now".
            if (originalTargetMs > 0L) putExtra(Constants.EXTRA_ORIGINAL_TARGET_MS, originalTargetMs)
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            alarmId.hashCode() + REQUEST_DISMISS,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePendingIntent = PendingIntent.getService(
            this,
            alarmId.hashCode() + REQUEST_SNOOZE,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = soundName
            ?.replace('_', ' ')
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            ?.let { "Sound: $it" }
            ?: "Time to start your day"

        return NotificationCompat.Builder(this, Constants.ALARM_PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(contentPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "Dismiss", dismissPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Snooze", snoozePendingIntent)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.ALARM_PLAYBACK_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(Constants.ALARM_PLAYBACK_NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.ALARM_PLAYBACK_CHANNEL_ID,
            "Alarm Playback",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Controls active alarm playback"
            setBypassDnd(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 1000)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun releaseMedia() {
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Throwable) {
        }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stopPlayback() {
        releaseMedia()
        getSystemService(NotificationManager::class.java).cancel(Constants.ALARM_NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseMedia()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "alarm_playback_start"
        const val ACTION_STOP = "alarm_playback_stop"
        const val ACTION_SNOOZE = "alarm_playback_snooze"
        const val EXTRA_ALARM_ID = Constants.EXTRA_ALARM_ID

        private const val REQUEST_DISMISS = 10_000
        private const val REQUEST_SNOOZE = 20_000
    }
}
