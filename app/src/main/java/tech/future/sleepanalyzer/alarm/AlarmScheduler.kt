package tech.future.sleepanalyzer.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import tech.future.sleepanalyzer.data.db.entity.AlarmConfig
import tech.future.sleepanalyzer.util.Constants
import tech.future.sleepanalyzer.util.PermissionsUtil
import java.util.Calendar

class AlarmScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: AlarmConfig): Boolean {
        cancel(alarm.id)
        if (!alarm.isEnabled) return PermissionsUtil.canScheduleExactAlarms(appContext)

        return if (alarm.useSmartWake && alarm.wakeWindowMinutes > 0) {
            val windowStartMs = calculateNextTriggerTime(alarm)
            val deadlineMs = calculateNextDeadlineTime(alarm)
            val exactWindow = schedulePendingIntent(windowStartMs, createWindowStartPendingIntent(alarm.id, windowStartMs, deadlineMs))
            val exactDeadline = schedulePendingIntent(deadlineMs, createDeadlinePendingIntent(alarm.id))
            exactWindow && exactDeadline
        } else {
            scheduleAt(alarm, calculateNextDeadlineTime(alarm))
        }
    }

    fun scheduleAt(alarm: AlarmConfig, triggerTime: Long): Boolean {
        cancel(alarm.id)
        return schedulePendingIntent(triggerTime, createDeadlinePendingIntent(alarm.id))
    }

    fun cancel(alarmId: Long) {
        cancelPendingIntent(createLegacyPendingIntent(alarmId))
        cancelPendingIntent(createWindowStartPendingIntent(alarmId))
        cancelPendingIntent(createDeadlinePendingIntent(alarmId))
    }

    private fun schedulePendingIntent(triggerTime: Long, pendingIntent: PendingIntent): Boolean {
        if (PermissionsUtil.canScheduleExactAlarms(appContext)) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                return true
            } catch (_: SecurityException) {
            }
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
        return false
    }

    private fun cancelPendingIntent(pendingIntent: PendingIntent) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun createLegacyPendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            putExtra(Constants.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createWindowStartPendingIntent(
        alarmId: Long,
        windowStartMs: Long = System.currentTimeMillis(),
        windowEndMs: Long = windowStartMs + 30L * 60 * 1000
    ): PendingIntent {
        val intent = Intent(appContext, SmartAlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("window_start_ms", windowStartMs)
            putExtra("window_end_ms", windowEndMs)
        }
        return PendingIntent.getBroadcast(
            appContext,
            alarmId.toInt() xor WINDOW_REQUEST_MASK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createDeadlinePendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            putExtra(Constants.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            alarmId.toInt() xor DEADLINE_REQUEST_MASK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun calculateNextTriggerTime(alarm: AlarmConfig): Long =
        calculateNextScheduledTime(alarm, subtractWakeWindow = true)

    private fun calculateNextDeadlineTime(alarm: AlarmConfig): Long =
        calculateNextScheduledTime(alarm, subtractWakeWindow = false)

    private fun calculateNextScheduledTime(alarm: AlarmConfig, subtractWakeWindow: Boolean): Long {
        val now = Calendar.getInstance()
        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val enabledDays = alarm.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        while (alarmTime.before(now) || (enabledDays.isNotEmpty() && calendarDayToOurDay(alarmTime.get(Calendar.DAY_OF_WEEK)) !in enabledDays)) {
            alarmTime.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (subtractWakeWindow) {
            alarmTime.add(Calendar.MINUTE, -alarm.wakeWindowMinutes)
        }
        return alarmTime.timeInMillis
    }

    private fun calendarDayToOurDay(calendarDay: Int): Int {
        return when (calendarDay) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    companion object {
        private const val WINDOW_REQUEST_MASK = 0x5555_DEAD
        private const val DEADLINE_REQUEST_MASK = 0x7777_DEAD
    }
}
