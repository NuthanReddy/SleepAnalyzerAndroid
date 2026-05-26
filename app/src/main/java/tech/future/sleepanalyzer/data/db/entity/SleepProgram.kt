package tech.future.sleepanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_programs")
data class SleepProgram(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val category: String, // "stress_relief", "bedroom_hacks", "screen_use", "sleep_hygiene", "relaxation"
    val steps: String = "", // JSON array of step strings
    val durationDays: Int = 7,
    val currentDay: Int = 0,
    val isStarted: Boolean = false,
    val isCompleted: Boolean = false,
    val startedAt: Long? = null
)
