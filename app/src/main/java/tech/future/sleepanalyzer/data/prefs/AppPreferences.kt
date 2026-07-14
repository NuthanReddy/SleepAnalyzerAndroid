package tech.future.sleepanalyzer.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sleep_analyzer_prefs")

data class AppPreferencesSnapshot(
    val setupCompleted: Boolean,
    val voiceIsolationEnabled: Boolean,
    val voiceIsolationAsked: Boolean,
    val micForStagingEnabled: Boolean,
    val recordAudioDuringTracking: Boolean,
    val noiseReductionEnabled: Boolean,
    val bedtimeAutoDetectEnabled: Boolean,
    val detailedHealthContextEnabled: Boolean,
    val programsSeeded: Boolean,
    val cloudSyncEnabled: Boolean,
    val lastDataRequestId: String?,
    val lastPromptTime: Long,
    val soundDefaultVolume: Float,
    val eventMergeGapMs: Long,
    val weeklyReportEnabled: Boolean
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val VOICE_ISOLATION_ENABLED = booleanPreferencesKey("voice_isolation_enabled")
        val VOICE_ISOLATION_ASKED = booleanPreferencesKey("voice_isolation_asked")
        val MIC_FOR_STAGING_ENABLED = booleanPreferencesKey("mic_for_staging_enabled")
        val RECORD_AUDIO_DURING_TRACKING = booleanPreferencesKey("record_audio_during_tracking")
        val NOISE_REDUCTION_ENABLED = booleanPreferencesKey("noise_reduction_enabled")
        val BEDTIME_AUTO_DETECT_ENABLED = booleanPreferencesKey("bedtime_auto_detect_enabled")
        val DETAILED_HEALTH_CONTEXT_ENABLED = booleanPreferencesKey("detailed_health_context_enabled")
        val PROGRAMS_SEEDED = booleanPreferencesKey("programs_seeded")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val LAST_DATA_REQUEST_ID = stringPreferencesKey("last_data_request_id")
        val LAST_PROMPT_TIME = longPreferencesKey("last_prompt_time")
        val SOUND_DEFAULT_VOLUME = floatPreferencesKey("sound_default_volume")
        val EVENT_MERGE_GAP_MS = longPreferencesKey("event_merge_gap_ms")
        val WEEKLY_REPORT_ENABLED = booleanPreferencesKey("weekly_report_enabled")
    }

    val setupCompletedFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.SETUP_COMPLETED] ?: false }
    val voiceIsolationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.VOICE_ISOLATION_ENABLED] ?: false }
    val voiceIsolationAskedFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.VOICE_ISOLATION_ASKED] ?: false }
    val micForStagingEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.MIC_FOR_STAGING_ENABLED] ?: false }
    val recordAudioDuringTrackingFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.RECORD_AUDIO_DURING_TRACKING] ?: false }
    /** Background-noise reduction (spectral subtraction) applied to the mic stream; on by default. */
    val noiseReductionEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOISE_REDUCTION_ENABLED] ?: true }
    val bedtimeAutoDetectEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.BEDTIME_AUTO_DETECT_ENABLED] ?: false }
    val detailedHealthContextEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.DETAILED_HEALTH_CONTEXT_ENABLED] ?: false }
    val programsSeededFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.PROGRAMS_SEEDED] ?: false }
    val cloudSyncEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLOUD_SYNC_ENABLED] ?: false }
    val lastDataRequestIdFlow: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_DATA_REQUEST_ID] }
    val soundDefaultVolumeFlow: Flow<Float> = context.dataStore.data.map { it[Keys.SOUND_DEFAULT_VOLUME] ?: 0.7f }
    val weeklyReportEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.WEEKLY_REPORT_ENABLED] ?: true }

    /**
     * Silence gap, in milliseconds, that must elapse before an in-progress sleep-sound event is
     * committed as its own recording. Larger values "club" bursts (snores, coughs) that are close
     * together into a single clip instead of many tiny chunks. Clamped to a sane 0.5s–5s range.
     */
    val eventMergeGapMsFlow: Flow<Long> = context.dataStore.data.map {
        (it[Keys.EVENT_MERGE_GAP_MS] ?: DEFAULT_EVENT_MERGE_GAP_MS).coerceIn(MIN_EVENT_MERGE_GAP_MS, MAX_EVENT_MERGE_GAP_MS)
    }

    suspend fun setSetupCompleted(value: Boolean) {
        context.dataStore.edit { it[Keys.SETUP_COMPLETED] = value }
    }

    suspend fun setVoiceIsolationEnabled(value: Boolean) {
        context.dataStore.edit {
            it[Keys.VOICE_ISOLATION_ENABLED] = value
            it[Keys.VOICE_ISOLATION_ASKED] = true
        }
    }

    suspend fun setMicForStagingEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.MIC_FOR_STAGING_ENABLED] = value }
    }

    suspend fun setRecordAudioDuringTracking(value: Boolean) {
        context.dataStore.edit { it[Keys.RECORD_AUDIO_DURING_TRACKING] = value }
    }

    suspend fun setNoiseReductionEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.NOISE_REDUCTION_ENABLED] = value }
    }

    suspend fun setBedtimeAutoDetectEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.BEDTIME_AUTO_DETECT_ENABLED] = value }
    }

    suspend fun setDetailedHealthContextEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.DETAILED_HEALTH_CONTEXT_ENABLED] = value }
    }

    suspend fun setProgramsSeeded(value: Boolean) {
        context.dataStore.edit { it[Keys.PROGRAMS_SEEDED] = value }
    }

    suspend fun setCloudSyncEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.CLOUD_SYNC_ENABLED] = value }
    }

    suspend fun setLastDataRequestId(value: String?) {
        context.dataStore.edit {
            if (value.isNullOrBlank()) {
                it.remove(Keys.LAST_DATA_REQUEST_ID)
            } else {
                it[Keys.LAST_DATA_REQUEST_ID] = value
            }
        }
    }

    suspend fun markVoiceIsolationAsked() {
        context.dataStore.edit { it[Keys.VOICE_ISOLATION_ASKED] = true }
    }

    suspend fun setSoundDefaultVolume(volume: Float) {
        context.dataStore.edit { it[Keys.SOUND_DEFAULT_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setEventMergeGapMs(gapMs: Long) {
        context.dataStore.edit {
            it[Keys.EVENT_MERGE_GAP_MS] = gapMs.coerceIn(MIN_EVENT_MERGE_GAP_MS, MAX_EVENT_MERGE_GAP_MS)
        }
    }

    suspend fun setWeeklyReportEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.WEEKLY_REPORT_ENABLED] = value }
    }

    suspend fun createBackupSnapshot(): AppPreferencesSnapshot {
        val values = context.dataStore.data.first()
        return AppPreferencesSnapshot(
            setupCompleted = values[Keys.SETUP_COMPLETED] ?: false,
            voiceIsolationEnabled = values[Keys.VOICE_ISOLATION_ENABLED] ?: false,
            voiceIsolationAsked = values[Keys.VOICE_ISOLATION_ASKED] ?: false,
            micForStagingEnabled = values[Keys.MIC_FOR_STAGING_ENABLED] ?: false,
            recordAudioDuringTracking = values[Keys.RECORD_AUDIO_DURING_TRACKING] ?: false,
            noiseReductionEnabled = values[Keys.NOISE_REDUCTION_ENABLED] ?: true,
            bedtimeAutoDetectEnabled = values[Keys.BEDTIME_AUTO_DETECT_ENABLED] ?: false,
            detailedHealthContextEnabled = values[Keys.DETAILED_HEALTH_CONTEXT_ENABLED] ?: false,
            programsSeeded = values[Keys.PROGRAMS_SEEDED] ?: false,
            cloudSyncEnabled = values[Keys.CLOUD_SYNC_ENABLED] ?: false,
            lastDataRequestId = values[Keys.LAST_DATA_REQUEST_ID],
            lastPromptTime = values[Keys.LAST_PROMPT_TIME] ?: 0L,
            soundDefaultVolume = values[Keys.SOUND_DEFAULT_VOLUME] ?: 0.7f,
            eventMergeGapMs = (values[Keys.EVENT_MERGE_GAP_MS] ?: DEFAULT_EVENT_MERGE_GAP_MS)
                .coerceIn(MIN_EVENT_MERGE_GAP_MS, MAX_EVENT_MERGE_GAP_MS),
            weeklyReportEnabled = values[Keys.WEEKLY_REPORT_ENABLED] ?: true
        )
    }

    suspend fun restoreBackupSnapshot(snapshot: AppPreferencesSnapshot) {
        context.dataStore.edit { values ->
            values[Keys.SETUP_COMPLETED] = snapshot.setupCompleted
            values[Keys.VOICE_ISOLATION_ENABLED] = snapshot.voiceIsolationEnabled
            values[Keys.VOICE_ISOLATION_ASKED] = snapshot.voiceIsolationAsked
            values[Keys.MIC_FOR_STAGING_ENABLED] = snapshot.micForStagingEnabled
            values[Keys.RECORD_AUDIO_DURING_TRACKING] = snapshot.recordAudioDuringTracking
            values[Keys.NOISE_REDUCTION_ENABLED] = snapshot.noiseReductionEnabled
            values[Keys.BEDTIME_AUTO_DETECT_ENABLED] = snapshot.bedtimeAutoDetectEnabled
            values[Keys.DETAILED_HEALTH_CONTEXT_ENABLED] = snapshot.detailedHealthContextEnabled
            values[Keys.PROGRAMS_SEEDED] = snapshot.programsSeeded
            values[Keys.CLOUD_SYNC_ENABLED] = snapshot.cloudSyncEnabled
            snapshot.lastDataRequestId?.let {
                values[Keys.LAST_DATA_REQUEST_ID] = it
            } ?: values.remove(Keys.LAST_DATA_REQUEST_ID)
            values[Keys.LAST_PROMPT_TIME] = snapshot.lastPromptTime
            values[Keys.SOUND_DEFAULT_VOLUME] = snapshot.soundDefaultVolume.coerceIn(0f, 1f)
            values[Keys.EVENT_MERGE_GAP_MS] = snapshot.eventMergeGapMs
                .coerceIn(MIN_EVENT_MERGE_GAP_MS, MAX_EVENT_MERGE_GAP_MS)
            values[Keys.WEEKLY_REPORT_ENABLED] = snapshot.weeklyReportEnabled
        }
    }

    companion object {
        const val DEFAULT_EVENT_MERGE_GAP_MS = 1500L
        const val MIN_EVENT_MERGE_GAP_MS = 500L
        const val MAX_EVENT_MERGE_GAP_MS = 5000L
    }
}
