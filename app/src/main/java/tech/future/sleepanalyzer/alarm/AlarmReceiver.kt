package tech.future.sleepanalyzer.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import tech.future.sleepanalyzer.MainActivity
import tech.future.sleepanalyzer.R
import tech.future.sleepanalyzer.service.AlarmPlaybackService
import tech.future.sleepanalyzer.util.Constants

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(Constants.EXTRA_ALARM_ID, -1L)
        if (alarmId < 0L) return

        // The DEADLINE alarm fires at the user's target wake time. Pass that timestamp through to
        // AlarmPlaybackService so snooze can re-arm from (target + snooze) instead of (now + snooze).
        // See backlog #10.
        val originalTargetMs = System.currentTimeMillis()

        val playbackIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_START
            putExtra(AlarmPlaybackService.EXTRA_ALARM_ID, alarmId)
            putExtra(Constants.EXTRA_ORIGINAL_TARGET_MS, originalTargetMs)
        }
        context.startForegroundService(playbackIntent)

        createNotificationChannel(context)
        showAlarmNotification(context, alarmId)
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            Constants.ALARM_CHANNEL_ID,
            "Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Sleep Analyzer Alarm"
            setBypassDnd(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun showAlarmNotification(context: Context, alarmId: Long) {
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Constants.EXTRA_NAVIGATE_TO, Constants.NAV_ALARM_RINGING)
            putExtra(Constants.EXTRA_ALARM_ID, alarmId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarmId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Constants.ALARM_CHANNEL_ID)
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

        context.getSystemService(NotificationManager::class.java)
            .notify(Constants.ALARM_NOTIFICATION_ID, notification)
    }
}
