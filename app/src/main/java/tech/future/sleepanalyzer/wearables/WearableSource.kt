package tech.future.sleepanalyzer.wearables

import tech.future.sleepanalyzer.data.db.entity.WearableDevice
import tech.future.sleepanalyzer.data.db.entity.WearableSample

/**
 * Strategy interface for biometric data providers.
 * Implementations should be safe to construct even when their underlying SDK is not installed -
 * they only fail in [isAvailable] / [hasPermissions] / [syncSince].
 *
 * Why a Strategy: Health Connect today, Samsung Health / Fitbit / Garmin / Wear OS tomorrow.
 * The rest of the app depends only on this interface.
 */
interface WearableSource {
    val id: String
    val displayName: String

    /** True if the provider is installed and reachable on this device. */
    suspend fun isAvailable(): Boolean

    /** Native permission identifiers the user must grant before sync. */
    suspend fun requiredPermissions(): Set<String>

    /** Whether all [requiredPermissions] are currently granted. */
    suspend fun hasPermissions(): Boolean

    /** Discoverable devices reported by the provider. */
    suspend fun listDevices(): List<WearableDevice>

    /**
     * Pull samples for [metrics] in (sinceMs..untilMs]. Implementations should return an empty list
     * (not throw) when there's nothing to sync; throw only on real provider errors so callers can retry.
     */
    suspend fun syncSince(
        sinceMs: Long,
        untilMs: Long,
        metrics: Set<WearableMetric>
    ): List<WearableSample>
}
