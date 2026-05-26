package tech.future.sleepanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_sessions")
data class SleepSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long, // epoch millis
    val endTime: Long? = null,
    val qualityScore: Int = 0, // 1-100
    val durationMinutes: Int = 0,
    val deepSleepMinutes: Int = 0,
    val lightSleepMinutes: Int = 0,
    val remSleepMinutes: Int = 0,
    val awakeMinutes: Int = 0,
    val interruptions: Int = 0,
    val moodBefore: String? = null, // "great", "good", "okay", "bad", "terrible"
    val moodAfter: String? = null,
    val isTracking: Boolean = false,
    val date: String = "" // YYYY-MM-DD for easy grouping
)
