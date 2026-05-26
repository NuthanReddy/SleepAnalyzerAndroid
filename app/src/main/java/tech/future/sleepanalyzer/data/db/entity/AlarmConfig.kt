package tech.future.sleepanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarm_configs")
data class AlarmConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hour: Int, // 0-23
    val minute: Int, // 0-59
    val isEnabled: Boolean = true,
    val wakeWindowMinutes: Int = 30, // 0-90
    val soundName: String = "gentle_chimes",
    val daysOfWeek: String = "1,2,3,4,5", // comma-separated day numbers (1=Mon..7=Sun)
    val isVibrationEnabled: Boolean = true,
    val snoozeEnabled: Boolean = true,
    val snoozeDurationMinutes: Int = 9,
    val label: String = "",
    /**
     * When true the AlarmScheduler additionally schedules a window-start broadcast and SmartWakeService
     * decides the actual fire moment within (hour:minute - wakeWindowMinutes, hour:minute).
     * When false only the deadline alarm is scheduled (old behavior).
     */
    val useSmartWake: Boolean = true
)

