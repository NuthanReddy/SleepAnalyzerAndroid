package tech.future.sleepanalyzer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tech.future.sleepanalyzer.service.SmartWakeService

class SmartAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("alarm_id", -1L)
        val windowStartMs = intent.getLongExtra("window_start_ms", System.currentTimeMillis())
        val windowEndMs = intent.getLongExtra("window_end_ms", windowStartMs + 30 * 60 * 1000)
        val pending = goAsync()
        try {
            val svc = Intent(context, SmartWakeService::class.java).apply {
                action = SmartWakeService.ACTION_START
                putExtra(SmartWakeService.EXTRA_ALARM_ID, alarmId)
                putExtra(SmartWakeService.EXTRA_WINDOW_START, windowStartMs)
                putExtra(SmartWakeService.EXTRA_WINDOW_END, windowEndMs)
            }
            context.startForegroundService(svc)
        } finally {
            pending.finish()
        }
    }
}
