package tech.future.sleepanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_goals")
data class SleepGoal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val targetBedtimeHour: Int = 23,
    val targetBedtimeMinute: Int = 0,
    val targetWakeHour: Int = 7,
    val targetWakeMinute: Int = 0,
    val targetDurationMinutes: Int = 480, // 8 hours
    val targetScore: Int = 80,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
