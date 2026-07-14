package tech.future.sleepanalyzer.sync

import org.json.JSONArray
import org.json.JSONObject
import tech.future.sleepanalyzer.data.prefs.AppPreferencesSnapshot

data class BackupSummary(
    val databaseRecords: Int,
    val mediaFiles: Int,
    val skippedLegacyRecordings: Int = 0
)

internal data class BackupMediaEntry(
    val databaseId: Long,
    val archivePath: String
)

internal data class BackupManifest(
    val formatVersion: Int,
    val databaseVersion: Int,
    val exportedAt: Long,
    val databaseRecords: Int,
    val preferences: AppPreferencesSnapshot,
    val recordings: List<BackupMediaEntry>,
    val voiceProfiles: List<BackupMediaEntry>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("format", FORMAT_NAME)
        put("formatVersion", formatVersion)
        put("databaseVersion", databaseVersion)
        put("exportedAt", exportedAt)
        put("databaseRecords", databaseRecords)
        put("preferences", preferences.toJson())
        put("recordings", recordings.toJson())
        put("voiceProfiles", voiceProfiles.toJson())
    }

    companion object {
        const val FORMAT_NAME = "sleep-analyzer-portable-backup"
        const val CURRENT_FORMAT_VERSION = 2

        fun fromJson(json: JSONObject): BackupManifest {
            require(json.optString("format") == FORMAT_NAME) { "Unsupported backup format" }
            return BackupManifest(
                formatVersion = json.getInt("formatVersion"),
                databaseVersion = json.getInt("databaseVersion"),
                exportedAt = json.getLong("exportedAt"),
                databaseRecords = json.optInt("databaseRecords", 0),
                preferences = preferencesFromJson(json.getJSONObject("preferences")),
                recordings = mediaEntriesFromJson(json.optJSONArray("recordings")),
                voiceProfiles = mediaEntriesFromJson(json.optJSONArray("voiceProfiles"))
            )
        }
    }
}

private fun List<BackupMediaEntry>.toJson(): JSONArray = JSONArray().apply {
    forEach { entry ->
        put(
            JSONObject()
                .put("databaseId", entry.databaseId)
                .put("archivePath", entry.archivePath)
        )
    }
}

private fun mediaEntriesFromJson(array: JSONArray?): List<BackupMediaEntry> {
    if (array == null) return emptyList()
    return buildList(array.length()) {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                BackupMediaEntry(
                    databaseId = item.getLong("databaseId"),
                    archivePath = item.getString("archivePath")
                )
            )
        }
    }
}

private fun AppPreferencesSnapshot.toJson(): JSONObject = JSONObject().apply {
    put("setupCompleted", setupCompleted)
    put("voiceIsolationEnabled", voiceIsolationEnabled)
    put("voiceIsolationAsked", voiceIsolationAsked)
    put("micForStagingEnabled", micForStagingEnabled)
    put("recordAudioDuringTracking", recordAudioDuringTracking)
    put("noiseReductionEnabled", noiseReductionEnabled)
    put("bedtimeAutoDetectEnabled", bedtimeAutoDetectEnabled)
    put("detailedHealthContextEnabled", detailedHealthContextEnabled)
    put("programsSeeded", programsSeeded)
    put("cloudSyncEnabled", cloudSyncEnabled)
    put("lastDataRequestId", lastDataRequestId)
    put("lastPromptTime", lastPromptTime)
    put("soundDefaultVolume", soundDefaultVolume.toDouble())
    put("eventMergeGapMs", eventMergeGapMs)
    put("weeklyReportEnabled", weeklyReportEnabled)
}

private fun preferencesFromJson(json: JSONObject): AppPreferencesSnapshot =
    AppPreferencesSnapshot(
        setupCompleted = json.optBoolean("setupCompleted", false),
        voiceIsolationEnabled = json.optBoolean("voiceIsolationEnabled", false),
        voiceIsolationAsked = json.optBoolean("voiceIsolationAsked", false),
        micForStagingEnabled = json.optBoolean("micForStagingEnabled", false),
        recordAudioDuringTracking = json.optBoolean("recordAudioDuringTracking", false),
        noiseReductionEnabled = json.optBoolean("noiseReductionEnabled", true),
        bedtimeAutoDetectEnabled = json.optBoolean("bedtimeAutoDetectEnabled", false),
        detailedHealthContextEnabled = json.optBoolean("detailedHealthContextEnabled", false),
        programsSeeded = json.optBoolean("programsSeeded", false),
        cloudSyncEnabled = json.optBoolean("cloudSyncEnabled", false),
        lastDataRequestId = if (json.isNull("lastDataRequestId")) {
            null
        } else {
            json.optString("lastDataRequestId").takeIf(String::isNotBlank)
        },
        lastPromptTime = json.optLong("lastPromptTime", 0L),
        soundDefaultVolume = json.optDouble("soundDefaultVolume", 0.7).toFloat(),
        eventMergeGapMs = json.optLong("eventMergeGapMs", 1_500L),
        weeklyReportEnabled = json.optBoolean("weeklyReportEnabled", true)
    )
