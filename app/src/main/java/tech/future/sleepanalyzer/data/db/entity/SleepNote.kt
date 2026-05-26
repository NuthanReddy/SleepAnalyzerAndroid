package tech.future.sleepanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sleep_notes")
data class SleepNote(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long? = null,
    val date: String, // YYYY-MM-DD
    val tags: String = "", // comma-separated: "coffee,stress,exercise,alcohol,late_meal,screen_time,medication"
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
