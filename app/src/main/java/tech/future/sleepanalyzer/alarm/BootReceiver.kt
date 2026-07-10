package tech.future.sleepanalyzer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.data.prefs.AppPreferences
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.service.BedtimeDetectionService

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

                // Resume opt-in bedtime auto-detection (#11) if the user left it enabled.
                val bedtimeEnabled = runCatching {
                    AppPreferences(appContext).bedtimeAutoDetectEnabledFlow.first()
                }.getOrDefault(false)
                if (bedtimeEnabled) {
                    val svc = Intent(appContext, BedtimeDetectionService::class.java)
                        .setAction(BedtimeDetectionService.ACTION_START)
                    runCatching { ContextCompat.startForegroundService(appContext, svc) }
                }
            } catch (_: Throwable) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
