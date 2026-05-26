package tech.future.sleepanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single biometric data point pulled from a wearable / health platform.
 * Multiple metrics share one table so the DAO is small and queries are uniform.
 */
@Entity(
    tableName = "wearable_samples",
    indices = [
        Index("timestamp"),
        Index("sessionId"),
        Index(value = ["deviceId", "metric", "timestamp"], unique = true)
    ]
)
data class WearableSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long? = null,
    val timestamp: Long,
    /** WearableMetric.name */
    val metric: String,
    val value: Float,
    val unit: String = "",
    /** Originating device id, e.g. "health_connect:com.fitbit.FitbitMobile" */
    val deviceId: String? = null,
    /** Source provider key, e.g. "health_connect", "samsung_health". */
    val sourceProvider: String = "health_connect"
)
