package tech.future.sleepanalyzer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.data.repository.SleepRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val repository = SleepRepository(appContext)
                val scheduler = AlarmScheduler(appContext)
                repository.getEnabledAlarmsList().forEach { alarm ->
                    scheduler.schedule(alarm)
                }
            } catch (_: Throwable) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
