package tech.future.sleepanalyzer.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Centralized dispatchers for the audio pipeline.
 * Capture runs on a dedicated single thread (low-latency, blocking AudioRecord.read).
 * Processing runs on a small fixed pool sized for CPU-bound FFT/feature work.
 * IO uses the shared IO dispatcher for files and database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object AudioDispatchers {

    private val captureExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AudioCapture").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
        }
    }

    private val processingExecutor = Executors.newFixedThreadPool(
        minOf(2, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
    ) { runnable ->
        Thread(runnable, "AudioProcessing").apply {
            priority = Thread.NORM_PRIORITY
            isDaemon = true
        }
    }

    val capture: CoroutineDispatcher = captureExecutor.asCoroutineDispatcher()
    val processing: CoroutineDispatcher = processingExecutor.asCoroutineDispatcher()
    val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)
}
