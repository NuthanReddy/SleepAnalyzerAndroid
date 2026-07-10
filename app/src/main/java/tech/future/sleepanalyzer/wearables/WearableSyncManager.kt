package tech.future.sleepanalyzer.wearables

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import tech.future.sleepanalyzer.data.db.entity.WearableDevice
import tech.future.sleepanalyzer.data.db.entity.WearableSample
import tech.future.sleepanalyzer.data.prefs.AppPreferences
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
        val synced = sources
            .map { src -> async { collectFromSource(src, startMs, endMs, metrics, sessionId) } }
            .awaitAll()

        val merged = mergeSyncedSources(synced)
        merged.devices.forEach { repository.upsertWearableDevice(it) }
        repository.insertWearableSamples(merged.samples)
        merged.devices.forEach { repository.markWearableSynced(it.id) }
        merged.samples.size
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
    suspend fun syncIncremental(metrics: Set<WearableMetric>? = null): Int {
        val effectiveMetrics = metrics ?: effectiveMetrics()
        val now = System.currentTimeMillis()
        val earliest = effectiveMetrics.mapNotNull { repository.getLatestWearableTimestamp(it.name) }
            .minOrNull() ?: (now - DEFAULT_BACKFILL_MS)
        val sampleCount = syncRange(earliest - 1L, now, effectiveMetrics)
        val latestStageEndMs = repository.getLatestVendorStageEndMs() ?: (now - DEFAULT_BACKFILL_MS)
        val stageCount = syncSleepStages(latestStageEndMs, now)
        return sampleCount + stageCount
    }

    /**
     * The metric set to sync: the core biometric set plus the opt-in "Detailed health context"
     * metrics (#7) when the user has enabled that preference.
     */
    private suspend fun effectiveMetrics(): Set<WearableMetric> {
        val detailed = runCatching {
            AppPreferences(context).detailedHealthContextEnabledFlow.first()
        }.getOrDefault(false)
        return if (detailed) DEFAULT_METRICS + DETAILED_METRICS else DEFAULT_METRICS
    }

    /** Result of collecting from a single source, before persistence. */
    internal data class SyncedSource(
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

        /** Opt-in "Detailed health context" (#7) metrics, synced only when the user enables them. */
        val DETAILED_METRICS: Set<WearableMetric> = setOf(
            WearableMetric.BODY_TEMPERATURE,
            WearableMetric.CAFFEINE,
            WearableMetric.HYDRATION
        )
        const val DEFAULT_BACKFILL_MS: Long = 24L * 60 * 60 * 1000

        /**
         * Query one source for its devices and samples. Never throws: permission checks and IO
         * are guarded so a single failing source degrades to an empty result instead of aborting
         * the whole sync. Samples are stamped with [sessionId] when they don't already carry one,
         * and devices seen only in sample metadata are synthesized so they show up in the UI.
         */
        internal suspend fun collectFromSource(
            src: WearableSource,
            startMs: Long,
            endMs: Long,
            metrics: Set<WearableMetric>,
            sessionId: Long?
        ): SyncedSource {
            val hasPermissions = runCatching { src.hasPermissions() }.getOrDefault(false)
            if (!hasPermissions) return SyncedSource()

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
            return SyncedSource(
                devices = (listedDevices + inferredDevices).distinctBy { it.id },
                samples = samples
            )
        }

        /** Merge per-source results: dedupe devices by id, flatten samples (DB dedupes samples). */
        internal fun mergeSyncedSources(results: List<SyncedSource>): SyncedSource = SyncedSource(
            devices = results.flatMap { it.devices }.distinctBy { it.id },
            samples = results.flatMap { it.samples }
        )

        private fun inferredDeviceName(deviceId: String, fallback: String): String {
            val token = deviceId.substringAfter(':', "").substringAfterLast('.', "")
            if (token.isBlank() || token == "default") return fallback
            return token
                .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
