package tech.future.sleepanalyzer

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.di.ServiceLocator
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application entry point.
 *
 * Responsibilities, in strict order:
 * 1. Install a default uncaught exception handler that writes the full stack trace to
 *    `filesDir/crash_log/last_crash.txt` BEFORE the process is killed. Without this,
 *    user-reported "the app crashes" reports are impossible to diagnose because the
 *    crash dialog gets dismissed and logcat output disappears on the next launch.
 * 2. Initialize the [ServiceLocator] so any code path that touches it (services,
 *    receivers, content providers) finds an initialized application context.
 *
 * Anything that could possibly throw at process start (Firebase, WorkManager, DataStore)
 * is wrapped in `runCatching` so a failure in one subsystem can never take down the
 * whole app. The handlers re-throw or surface the failure to logcat only.
 */
class SleepAnalyzerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Crash logging first so anything below us can be captured.
        installCrashHandler()

        // 2. Service locator — needed by every service / receiver in the app.
        runCatching { ServiceLocator.initialize(this) }
            .onFailure { Log.e(TAG, "ServiceLocator.initialize failed", it) }

        // 3. Health Connect / wearable periodic sync. Best-effort; never fatal.
        runCatching {
            tech.future.sleepanalyzer.wearables.WearableScheduler.schedulePeriodic(this)
        }.onFailure { Log.w(TAG, "WearableScheduler.schedulePeriodic failed", it) }

        // 4. Seed sleep programs on first run so the Programs screen is never empty.
        seedProgramsOnFirstRun()
    }

    private fun seedProgramsOnFirstRun() {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        appScope.launch {
            runCatching {
                val prefs = ServiceLocator.preferences
                val repo = ServiceLocator.repository
                val alreadySeeded = prefs.programsSeededFlow.firstOrNull() ?: false
                val existing = repo.getAllPrograms().firstOrNull() ?: emptyList()
                // Re-seed if the flag says we did, but the table is empty (e.g. destructive migration wiped it).
                if (!alreadySeeded || existing.isEmpty()) {
                    val existingTitles = existing.map { it.title }.toSet()
                    tech.future.sleepanalyzer.data.seed.ProgramSeeds.defaults.forEach { program ->
                        if (program.title !in existingTitles) {
                            repo.insertProgram(program)
                        }
                    }
                    prefs.setProgramsSeeded(true)
                    Log.d(TAG, "Seeded ${tech.future.sleepanalyzer.data.seed.ProgramSeeds.defaults.size} sleep programs")
                }
            }.onFailure { Log.w(TAG, "Program seeding failed", it) }
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val dir = File(filesDir, "crash_log").apply { mkdirs() }
        val target = File(dir, "last_crash.txt")
        val timestampFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                PrintWriter(target).use { w ->
                    w.println("=== Sleep Analyzer crash ===")
                    w.println("Time   : ${timestampFmt.format(Date())}")
                    w.println("Thread : ${thread.name}")
                    w.println("Message: ${throwable.message}")
                    w.println()
                    throwable.printStackTrace(w)
                    var cause: Throwable? = throwable.cause
                    while (cause != null) {
                        w.println()
                        w.println("Caused by: ${cause.javaClass.name}: ${cause.message}")
                        cause.printStackTrace(w)
                        cause = cause.cause
                    }
                }
                Log.e(TAG, "Wrote crash log to ${target.absolutePath}", throwable)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to write crash log", e)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "SleepAnalyzerApp"
    }
}
