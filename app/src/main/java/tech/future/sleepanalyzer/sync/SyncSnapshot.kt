package tech.future.sleepanalyzer.sync

data class ProfileDoc(
    val uid: String,
    val displayName: String?,
    val dateOfBirth: Long?,
    val biologicalSex: String?,
    val heightCm: Float?,
    val weightKg: Float?,
    val activityLevel: String?,
    val units: String,
    val sleepConditions: List<String>,
    val medications: String?,
    val typicalCaffeineCutoffHour: Int?,
    val shiftWorkSchedule: String?,
    val updatedAt: Long
)

data class GoalDoc(
    val goalId: Long,
    val targetBedtimeHour: Int,
    val targetBedtimeMinute: Int,
    val targetWakeHour: Int,
    val targetWakeMinute: Int,
    val targetDurationMinutes: Int,
    val targetScore: Int,
    val isActive: Boolean,
    val updatedAt: Long
)

data class DailySummaryDoc(
    val date: String,
    val qualityScore: Int,
    val durationMinutes: Int,
    val deepSleepMinutes: Int,
    val lightSleepMinutes: Int,
    val remSleepMinutes: Int,
    val awakeMinutes: Int,
    val interruptions: Int,
    val snoreEvents: Int,
    val coughEvents: Int,
    val talkEvents: Int,
    val attributionSummary: Map<String, Int>
)

data class SessionDoc(
    val sessionId: Long,
    val startTime: Long,
    val endTime: Long?,
    val durationMinutes: Int,
    val qualityScore: Int,
    val stagesBreakdown: Map<String, Int>,
    val moodBefore: String?,
    val moodAfter: String?,
    val date: String
)

data class AudioEventDoc(
    val recordingId: Long,
    val sessionId: Long?,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val type: String,
    val attributedTo: String,
    val matchConfidence: Float,
    val pitchHz: Float,
    val maxAmplitude: Int,
    val croppedFromMs: Int,
    val croppedToMs: Int,
    val date: String
)

data class NoteDoc(
    val noteId: Long,
    val sessionId: Long?,
    val date: String,
    val tags: List<String>,
    val note: String,
    val createdAt: Long
)
