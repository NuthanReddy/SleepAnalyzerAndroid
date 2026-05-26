package tech.future.sleepanalyzer.wearables

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tech.future.sleepanalyzer.data.db.entity.WearableDevice
import tech.future.sleepanalyzer.data.db.entity.WearableSample
import tech.future.sleepanalyzer.data.repository.SleepRepository

/**
 * Coordinates one-shot and periodic sync of biometric data from all available sources.
 * Each source is queried in parallel; results are deduped via the unique index on
 * (deviceId, metric, timestamp) in WearableSample.
 */
class WearableSyncManager(
    private val context: Context,
    private val repository: SleepRepository
) {

    /**
     * Pull metrics for the given range from every available source.
     * Returns the total number of samples persisted.
     */
    suspend fun syncRange(
        startMs: Long,
        endMs: Long,
        metrics: Set<WearableMetric> = DEFAULT_METRICS,
        sessionId: Long? = null
    ): Int = coroutineScope {
        val sources = SourceRegistry.available(context)
        val syncedSources = sources.map { src ->
            async {
                val hasPermissions = runCatching { src.hasPermissions() }.getOrDefault(false)
                if (!hasPermissions) return@async SyncedSource()

                val listedDevices = runCatching { src.listDevices() }.getOrDefault(emptyList())
                val samples = runCatching { src.syncSince(startMs, endMs, metrics) }
                    .getOrDefault(emptyList())
                    .map { it.copy(sessionId = it.sessionId ?: sessionId) }
                val inferredDevices = samples
                    .mapNotNull { sample ->
                        sample.deviceId?.takeIf { deviceId -> listedDevices.none { it.id == deviceId } }?.let { deviceId ->
                            WearableDevice(
                                id = deviceId,
                                displayName = inferredDeviceName(deviceId, src.displayName),
                                sourceProvider = src.id,
                                capabilities = metrics.joinToString(",") { metric -> metric.name }
                            )
                        }
                    }
                SyncedSource(
                    devices = (listedDevices + inferredDevices).distinctBy { it.id },
                    samples = samples
                )
            }
        }.awaitAll()

        val devices = syncedSources.flatMap { it.devices }.distinctBy { it.id }
        devices.forEach { repository.upsertWearableDevice(it) }
        repository.insertWearableSamples(syncedSources.flatMap { it.samples })
        devices.forEach { repository.markWearableSynced(it.id) }
        syncedSources.sumOf { it.samples.size }
    }

    suspend fun syncSleepStages(sinceMs: Long, untilMs: Long): Int = coroutineScope {
        val stageRows = SourceRegistry.available(context)
            .mapNotNull { it as? HealthConnectSource }
            .map { source ->
                async {
                    runCatching { source.syncSleepSessions(sinceMs, untilMs) }
                        .getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()

        repository.insertVendorStageSegments(stageRows)
    }

    /** Sync since each metric's last-known timestamp; used by the periodic worker. */
    suspend fun syncIncremental(metrics: Set<WearableMetric> = DEFAULT_METRICS): Int {
        val now = System.currentTimeMillis()
        val earliest = metrics.mapNotNull { repository.getLatestWearableTimestamp(it.name) }
            .minOrNull() ?: (now - DEFAULT_BACKFILL_MS)
        val sampleCount = syncRange(earliest - 1L, now, metrics)
        val latestStageEndMs = repository.getLatestVendorStageEndMs() ?: (now - DEFAULT_BACKFILL_MS)
        val stageCount = syncSleepStages(latestStageEndMs, now)
        return sampleCount + stageCount
    }

    private fun inferredDeviceName(deviceId: String, fallback: String): String {
        val token = deviceId.substringAfter(':', "").substringAfterLast('.', "")
        if (token.isBlank() || token == "default") return fallback
        return token
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private data class SyncedSource(
        val devices: List<WearableDevice> = emptyList(),
        val samples: List<WearableSample> = emptyList()
    )

    companion object {
        val DEFAULT_METRICS: Set<WearableMetric> = setOf(
            WearableMetric.HEART_RATE,
            WearableMetric.RESTING_HEART_RATE,
            WearableMetric.HRV_RMSSD,
            WearableMetric.RESPIRATORY_RATE,
            WearableMetric.SPO2
        )
        const val DEFAULT_BACKFILL_MS: Long = 24L * 60 * 60 * 1000
    }
}
