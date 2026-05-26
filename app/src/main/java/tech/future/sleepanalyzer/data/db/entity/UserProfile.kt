package tech.future.sleepanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Singleton user profile (id always 1). All fields nullable so the user can skip them.
 * Used to personalize sleep scoring, HR/HRV baselines, and notification suggestions.
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val id: Long = 1L,
    val displayName: String? = null,
    /** Epoch millis. Used to compute age for stage cycle adjustments. */
    val dateOfBirth: Long? = null,
    /** "male" / "female" / "other" / null. Affects HR baselines. */
    val biologicalSex: String? = null,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    /** "sedentary" / "light" / "moderate" / "active" / "athlete" */
    val activityLevel: String? = null,
    /** "metric" / "imperial" */
    val units: String = "metric",
    /** CSV of conditions: sleep_apnea, insomnia, restless_legs, narcolepsy */
    val sleepConditions: String = "",
    val medications: String? = null,
    /** Hour of day (0..23) when the user stops caffeine. */
    val typicalCaffeineCutoffHour: Int? = null,
    /** "none" / "early" / "late" / "rotating" / "night" */
    val shiftWorkSchedule: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val ageYears: Int? get() = dateOfBirth?.let {
        val ageMillis = System.currentTimeMillis() - it
        (ageMillis / (1000L * 60 * 60 * 24 * 365)).toInt()
    }

    val conditionsList: List<String> get() = sleepConditions.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val SINGLETON_ID = 1L
        fun empty() = UserProfile(id = SINGLETON_ID)
    }
}
