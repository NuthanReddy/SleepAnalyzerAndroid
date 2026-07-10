package tech.future.sleepanalyzer.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sleep_analyzer_prefs")

class AppPreferences(private val context: Context) {

    private object Keys {
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val VOICE_ISOLATION_ENABLED = booleanPreferencesKey("voice_isolation_enabled")
        val VOICE_ISOLATION_ASKED = booleanPreferencesKey("voice_isolation_asked")
        val MIC_FOR_STAGING_ENABLED = booleanPreferencesKey("mic_for_staging_enabled")
        val BEDTIME_AUTO_DETECT_ENABLED = booleanPreferencesKey("bedtime_auto_detect_enabled")
        val DETAILED_HEALTH_CONTEXT_ENABLED = booleanPreferencesKey("detailed_health_context_enabled")
        val PROGRAMS_SEEDED = booleanPreferencesKey("programs_seeded")
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val LAST_DATA_REQUEST_ID = stringPreferencesKey("last_data_request_id")
        val LAST_PROMPT_TIME = longPreferencesKey("last_prompt_time")
        val SOUND_DEFAULT_VOLUME = floatPreferencesKey("sound_default_volume")
    }

    val setupCompletedFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.SETUP_COMPLETED] ?: false }
    val voiceIsolationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.VOICE_ISOLATION_ENABLED] ?: false }
    val voiceIsolationAskedFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.VOICE_ISOLATION_ASKED] ?: false }
    val micForStagingEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.MIC_FOR_STAGING_ENABLED] ?: false }
    val bedtimeAutoDetectEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.BEDTIME_AUTO_DETECT_ENABLED] ?: false }
    val detailedHealthContextEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.DETAILED_HEALTH_CONTEXT_ENABLED] ?: false }
    val programsSeededFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.PROGRAMS_SEEDED] ?: false }
    val cloudSyncEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.CLOUD_SYNC_ENABLED] ?: false }
    val lastDataRequestIdFlow: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_DATA_REQUEST_ID] }
    val soundDefaultVolumeFlow: Flow<Float> = context.dataStore.data.map { it[Keys.SOUND_DEFAULT_VOLUME] ?: 0.7f }

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
}
