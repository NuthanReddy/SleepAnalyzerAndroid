package tech.future.sleepanalyzer.sync

import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import tech.future.sleepanalyzer.data.db.entity.AudioRecording
import tech.future.sleepanalyzer.data.db.entity.SleepGoal
import tech.future.sleepanalyzer.data.db.entity.SleepNote
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.db.entity.UserProfile
import tech.future.sleepanalyzer.data.repository.SleepRepository
import java.io.InputStream
import java.io.OutputStream

/**
 * Serializes all on-device sleep data to a single JSON document and restores it. Unlike
 * [SyncRepository] (Firebase cloud sync), this writes to a user-chosen file via the Storage Access
 * Framework, so the export survives an app reinstall without any account. Audio *files* are not
 * embedded (only their metadata); the WAV captures live under app-private storage and are covered by
 * Android Auto Backup instead.
 */
class LocalBackupManager(private val repository: SleepRepository) {

    /** Writes a full backup to [output]. Returns the number of records written. */
    suspend fun exportTo(output: OutputStream): Result<Int> = runCatching {
        val sessions = repository.getAllSessions().first()
        val recordings = repository.getAllRecordings().first()
        val notes = repository.getAllNotes().first()
        val profile = repository.getUserProfile()
        val goal = repository.getActiveGoalSync()

        val root = JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            profile?.let { put("profile", profileJson(it)) }
            goal?.let { put("goal", goalJson(it)) }
            put("sessions", JSONArray().apply { sessions.forEach { put(sessionJson(it)) } })
            put("recordings", JSONArray().apply { recordings.forEach { put(recordingJson(it)) } })
            put("notes", JSONArray().apply { notes.forEach { put(noteJson(it)) } })
        }

        output.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
        sessions.size + recordings.size + notes.size + (if (profile != null) 1 else 0) + (if (goal != null) 1 else 0)
    }

    /** Restores a backup previously produced by [exportTo]. Returns the number of records restored. */
    suspend fun importFrom(input: InputStream): Result<Int> = runCatching {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        var restored = 0

        root.optJSONObject("profile")?.let {
            repository.upsertUserProfile(profileFrom(it))
            restored++
        }
        root.optJSONObject("goal")?.let {
            runCatching { repository.insertGoal(goalFrom(it)) }
            restored++
        }
        root.optJSONArray("sessions")?.let { arr ->
            for (i in 0 until arr.length()) {
                runCatching { repository.insertSession(sessionFrom(arr.getJSONObject(i))) }
                restored++
            }
        }
        root.optJSONArray("recordings")?.let { arr ->
            for (i in 0 until arr.length()) {
                runCatching { repository.insertRecording(recordingFrom(arr.getJSONObject(i))) }
                restored++
            }
        }
        root.optJSONArray("notes")?.let { arr ->
            for (i in 0 until arr.length()) {
                runCatching { repository.insertNote(noteFrom(arr.getJSONObject(i))) }
                restored++
            }
        }
        restored
    }

    // --- Serialization ------------------------------------------------------

    private fun profileJson(p: UserProfile) = JSONObject().apply {
        put("id", p.id)
        put("displayName", p.displayName)
        put("dateOfBirth", p.dateOfBirth)
        put("biologicalSex", p.biologicalSex)
        put("heightCm", p.heightCm?.toDouble())
        put("weightKg", p.weightKg?.toDouble())
        put("activityLevel", p.activityLevel)
        put("units", p.units)
        put("sleepConditions", p.sleepConditions)
        put("medications", p.medications)
        put("typicalCaffeineCutoffHour", p.typicalCaffeineCutoffHour)
        put("shiftWorkSchedule", p.shiftWorkSchedule)
        put("updatedAt", p.updatedAt)
    }

    private fun goalJson(g: SleepGoal) = JSONObject().apply {
        put("id", g.id)
        put("targetBedtimeHour", g.targetBedtimeHour)
        put("targetBedtimeMinute", g.targetBedtimeMinute)
        put("targetWakeHour", g.targetWakeHour)
        put("targetWakeMinute", g.targetWakeMinute)
        put("targetDurationMinutes", g.targetDurationMinutes)
        put("targetScore", g.targetScore)
        put("isActive", g.isActive)
        put("createdAt", g.createdAt)
    }

    private fun sessionJson(s: SleepSession) = JSONObject().apply {
        put("id", s.id)
        put("startTime", s.startTime)
        put("endTime", s.endTime)
        put("qualityScore", s.qualityScore)
        put("durationMinutes", s.durationMinutes)
        put("deepSleepMinutes", s.deepSleepMinutes)
        put("lightSleepMinutes", s.lightSleepMinutes)
        put("remSleepMinutes", s.remSleepMinutes)
        put("awakeMinutes", s.awakeMinutes)
        put("interruptions", s.interruptions)
        put("moodBefore", s.moodBefore)
        put("moodAfter", s.moodAfter)
        put("date", s.date)
    }

    private fun recordingJson(r: AudioRecording) = JSONObject().apply {
        put("id", r.id)
        put("sessionId", r.sessionId)
        put("filePath", r.filePath)
        put("startTime", r.startTime)
        put("endTime", r.endTime)
        put("durationSeconds", r.durationSeconds)
        put("type", r.type)
        put("maxAmplitude", r.maxAmplitude)
        put("date", r.date)
        put("attributedTo", r.attributedTo)
        put("matchConfidence", r.matchConfidence.toDouble())
        put("pitchHz", r.pitchHz.toDouble())
        put("croppedFromMs", r.croppedFromMs)
        put("croppedToMs", r.croppedToMs)
        put("transcript", r.transcript)
    }

    private fun noteJson(n: SleepNote) = JSONObject().apply {
        put("id", n.id)
        put("sessionId", n.sessionId)
        put("date", n.date)
        put("tags", n.tags)
        put("note", n.note)
        put("createdAt", n.createdAt)
    }

    // --- Deserialization ----------------------------------------------------

    private fun profileFrom(j: JSONObject) = UserProfile(
        id = j.optLong("id", UserProfile.SINGLETON_ID),
        displayName = j.optStringOrNull("displayName"),
        dateOfBirth = j.optLongOrNull("dateOfBirth"),
        biologicalSex = j.optStringOrNull("biologicalSex"),
        heightCm = j.optFloatOrNull("heightCm"),
        weightKg = j.optFloatOrNull("weightKg"),
        activityLevel = j.optStringOrNull("activityLevel"),
        units = j.optString("units", "metric"),
        sleepConditions = j.optString("sleepConditions", ""),
        medications = j.optStringOrNull("medications"),
        typicalCaffeineCutoffHour = j.optIntOrNull("typicalCaffeineCutoffHour"),
        shiftWorkSchedule = j.optStringOrNull("shiftWorkSchedule"),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun goalFrom(j: JSONObject) = SleepGoal(
        id = j.optLong("id", 0L),
        targetBedtimeHour = j.optInt("targetBedtimeHour", 23),
        targetBedtimeMinute = j.optInt("targetBedtimeMinute", 0),
        targetWakeHour = j.optInt("targetWakeHour", 7),
        targetWakeMinute = j.optInt("targetWakeMinute", 0),
        targetDurationMinutes = j.optInt("targetDurationMinutes", 480),
        targetScore = j.optInt("targetScore", 80),
        isActive = j.optBoolean("isActive", true),
        createdAt = j.optLong("createdAt", System.currentTimeMillis())
    )

    private fun sessionFrom(j: JSONObject) = SleepSession(
        id = j.optLong("id", 0L),
        startTime = j.optLong("startTime", 0L),
        endTime = j.optLongOrNull("endTime"),
        qualityScore = j.optInt("qualityScore", 0),
        durationMinutes = j.optInt("durationMinutes", 0),
        deepSleepMinutes = j.optInt("deepSleepMinutes", 0),
        lightSleepMinutes = j.optInt("lightSleepMinutes", 0),
        remSleepMinutes = j.optInt("remSleepMinutes", 0),
        awakeMinutes = j.optInt("awakeMinutes", 0),
        interruptions = j.optInt("interruptions", 0),
        moodBefore = j.optStringOrNull("moodBefore"),
        moodAfter = j.optStringOrNull("moodAfter"),
        isTracking = false,
        date = j.optString("date", "")
    )

    private fun recordingFrom(j: JSONObject) = AudioRecording(
        id = j.optLong("id", 0L),
        sessionId = j.optLongOrNull("sessionId"),
        filePath = j.optString("filePath", ""),
        startTime = j.optLong("startTime", 0L),
        endTime = j.optLong("endTime", 0L),
        durationSeconds = j.optInt("durationSeconds", 0),
        type = j.optString("type", "unknown"),
        maxAmplitude = j.optInt("maxAmplitude", 0),
        date = j.optString("date", ""),
        attributedTo = j.optString("attributedTo", "unknown"),
        matchConfidence = j.optDouble("matchConfidence", 0.0).toFloat(),
        pitchHz = j.optDouble("pitchHz", 0.0).toFloat(),
        croppedFromMs = j.optInt("croppedFromMs", 0),
        croppedToMs = j.optInt("croppedToMs", 0),
        transcript = if (j.isNull("transcript")) null else j.optString("transcript", null)
    )

    private fun noteFrom(j: JSONObject) = SleepNote(
        id = j.optLong("id", 0L),
        sessionId = j.optLongOrNull("sessionId"),
        date = j.optString("date", ""),
        tags = j.optString("tags", ""),
        note = j.optString("note", ""),
        createdAt = j.optLong("createdAt", System.currentTimeMillis())
    )

    companion object {
        const val BACKUP_VERSION = 1
        const val FILE_PREFIX = "sleep_analyzer_backup"
    }
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name, "").takeIf { it.isNotEmpty() }

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optFloatOrNull(name: String): Float? =
    if (has(name) && !isNull(name)) optDouble(name).toFloat() else null
