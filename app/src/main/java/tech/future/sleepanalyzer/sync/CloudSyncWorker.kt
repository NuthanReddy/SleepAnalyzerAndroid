package tech.future.sleepanalyzer.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.firstOrNull
import tech.future.sleepanalyzer.auth.AuthState
import tech.future.sleepanalyzer.di.ServiceLocator

class CloudSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ServiceLocator.initialize(applicationContext)
        val authState = ServiceLocator.authRepository.state.value
        val syncEnabled = ServiceLocator.preferences.cloudSyncEnabledFlow.firstOrNull() == true
        if (authState is AuthState.SignedIn && syncEnabled) {
            val sinceMs = ServiceLocator.repository.getUserAccount()?.lastSyncMs ?: 0L
            return ServiceLocator.syncRepository.uploadIncremental(authState.uid, sinceMs)
                .fold(
                    onSuccess = { Result.success() },
                    onFailure = { Result.retry() }
                )
        }
        return Result.success()
    }
}
