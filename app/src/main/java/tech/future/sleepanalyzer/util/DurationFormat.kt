package tech.future.sleepanalyzer.util

/**
 * Formats an elapsed duration for display, keeping seconds precision for short sessions.
 *
 * Sessions are persisted as whole minutes, so anything under a minute would otherwise floor to
 * "0h 0m". Callers should pass the elapsed time in milliseconds (typically endTime - startTime).
 */
fun formatSleepDuration(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
