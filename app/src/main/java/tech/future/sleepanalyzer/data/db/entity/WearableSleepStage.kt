package tech.future.sleepanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wearable_sleep_stages",
    indices = [
        Index("startTime"),
        Index("sessionId"),
        Index(value = ["deviceId", "startTime", "endTime"], unique = true)
    ]
)
data class WearableSleepStage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long? = null,
    val startTime: Long,
    val endTime: Long,
    /** SleepStage.key — "awake"/"light"/"deep"/"rem" (use SleepStage.fromKey to map back). */
    val stage: String,
    val sourceProvider: String = "health_connect",
    val deviceId: String? = null
)
