package tech.future.sleepanalyzer.sync

import org.json.JSONObject
import tech.future.sleepanalyzer.data.db.entity.SleepGoal
import tech.future.sleepanalyzer.data.db.entity.SleepNote
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.db.entity.UserProfile
import tech.future.sleepanalyzer.data.repository.SleepRepository
import java.io.InputStream

/**
 * Compatibility reader for version-1 JSON exports. Those exports never contained audio bytes, so
 * recording metadata is deliberately skipped instead of restoring rows that cannot be played.
 */
internal class LegacyJsonBackupImporter(
    private val repository: SleepRepository
) {
    suspend fun importFrom(input: InputStream): BackupSummary {
        val root = JSONObject(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
        var restored = 0

        root.optJSONObject("profile")?.let { json ->
            runCatching { repository.upsertUserProfile(profileFrom(json)) }
                .onSuccess { restored++ }
                .getOrThrow()
        }
        root.optJSONObject("goal")?.let { json ->
            runCatching { repository.insertGoal(goalFrom(json)) }
                .onSuccess { restored++ }
                .getOrThrow()
        }
        root.optJSONArray("sessions")?.let { array ->
            for (index in 0 until array.length()) {
                repository.insertSession(sessionFrom(array.getJSONObject(index)))
                restored++
            }
        }
        root.optJSONArray("notes")?.let { array ->
            for (index in 0 until array.length()) {
                repository.insertNote(noteFrom(array.getJSONObject(index)))
                restored++
            }
        }

        return BackupSummary(
            databaseRecords = restored,
            mediaFiles = 0,
            skippedLegacyRecordings = root.optJSONArray("recordings")?.length() ?: 0
        )
    }

    private fun profileFrom(json: JSONObject) = UserProfile(
        id = json.optLong("id", UserProfile.SINGLETON_ID),
        displayName = json.optStringOrNull("displayName"),
        dateOfBirth = json.optLongOrNull("dateOfBirth"),
        biologicalSex = json.optStringOrNull("biologicalSex"),
        heightCm = json.optFloatOrNull("heightCm"),
        weightKg = json.optFloatOrNull("weightKg"),
        activityLevel = json.optStringOrNull("activityLevel"),
        units = json.optString("units", "metric"),
        sleepConditions = json.optString("sleepConditions", ""),
        medications = json.optStringOrNull("medications"),
        typicalCaffeineCutoffHour = json.optIntOrNull("typicalCaffeineCutoffHour"),
        shiftWorkSchedule = json.optStringOrNull("shiftWorkSchedule"),
        updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun goalFrom(json: JSONObject) = SleepGoal(
        id = json.optLong("id", 0L),
        targetBedtimeHour = json.optInt("targetBedtimeHour", 23),
        targetBedtimeMinute = json.optInt("targetBedtimeMinute", 0),
        targetWakeHour = json.optInt("targetWakeHour", 7),
        targetWakeMinute = json.optInt("targetWakeMinute", 0),
        targetDurationMinutes = json.optInt("targetDurationMinutes", 480),
        targetScore = json.optInt("targetScore", 80),
        isActive = json.optBoolean("isActive", true),
        createdAt = json.optLong("createdAt", System.currentTimeMillis())
    )

    private fun sessionFrom(json: JSONObject) = SleepSession(
        id = json.optLong("id", 0L),
        startTime = json.optLong("startTime", 0L),
        endTime = json.optLongOrNull("endTime"),
        qualityScore = json.optInt("qualityScore", 0),
        durationMinutes = json.optInt("durationMinutes", 0),
        deepSleepMinutes = json.optInt("deepSleepMinutes", 0),
        lightSleepMinutes = json.optInt("lightSleepMinutes", 0),
        remSleepMinutes = json.optInt("remSleepMinutes", 0),
        awakeMinutes = json.optInt("awakeMinutes", 0),
        interruptions = json.optInt("interruptions", 0),
        moodBefore = json.optStringOrNull("moodBefore"),
        moodAfter = json.optStringOrNull("moodAfter"),
        isTracking = false,
        date = json.optString("date", "")
    )

    private fun noteFrom(json: JSONObject) = SleepNote(
        id = json.optLong("id", 0L),
        sessionId = json.optLongOrNull("sessionId"),
        date = json.optString("date", ""),
        tags = json.optString("tags", ""),
        note = json.optString("note", ""),
        createdAt = json.optLong("createdAt", System.currentTimeMillis())
    )
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name, "").takeIf(String::isNotEmpty)

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optFloatOrNull(name: String): Float? =
    if (has(name) && !isNull(name)) optDouble(name).toFloat() else null
