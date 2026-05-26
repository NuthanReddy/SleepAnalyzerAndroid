package tech.future.sleepanalyzer.util

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun <T> Task<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        when {
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(
                task.exception ?: IllegalStateException("Google Play services task failed")
            )
        }
    }
}

suspend fun Task<*>.awaitCompletion(): Unit = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        when {
            task.isSuccessful -> continuation.resume(Unit)
            else -> continuation.resumeWithException(
                task.exception ?: IllegalStateException("Google Play services task failed")
            )
        }
    }
}
