package tech.future.sleepanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent record of a wearable device discovered via a [WearableSource].
 * The id is the source-qualified identifier (e.g. "health_connect:com.fitbit.FitbitMobile")
 * so multiple providers can coexist without collisions.
 */
@Entity(tableName = "wearable_devices")
data class WearableDevice(
    @PrimaryKey
    val id: String,
    val displayName: String,
    /** "watch" / "band" / "ring" / "phone" / "unknown" */
    val type: String = "unknown",
    /** "health_connect" / "samsung_health" / "fitbit" / "garmin" / "wear_os" */
    val sourceProvider: String,
    val isActive: Boolean = true,
    val lastSyncMs: Long = 0L,
    /** CSV of WearableMetric.name supported by this device. */
    val capabilities: String = ""
)
