package tech.future.sleepanalyzer.wearables

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import tech.future.sleepanalyzer.di.ServiceLocator

class WearableSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        ServiceLocator.wearableSyncManager().syncIncremental()
        Result.success()
    } catch (_: Throwable) {
        Result.retry()
    }
}
