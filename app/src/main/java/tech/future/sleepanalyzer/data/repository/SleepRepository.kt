package tech.future.sleepanalyzer.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.data.db.AppDatabase
import tech.future.sleepanalyzer.data.db.entity.*
import tech.future.sleepanalyzer.sleep.SleepStage
import tech.future.sleepanalyzer.sleep.VendorStageSegment
import tech.future.sleepanalyzer.wearables.WearableMetric

class SleepRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val sessionDao = db.sleepSessionDao()
    private val noteDao = db.sleepNoteDao()
    private val alarmDao = db.alarmConfigDao()
    private val recordingDao = db.audioRecordingDao()
    private val goalDao = db.sleepGoalDao()
    private val programDao = db.sleepProgramDao()
    private val voiceProfileDao = db.voiceProfileDao()

    // Sleep Sessions
    suspend fun insertSession(session: SleepSession): Long = sessionDao.insert(session)
    suspend fun updateSession(session: SleepSession) = sessionDao.update(session)
    suspend fun deleteSession(session: SleepSession) = sessionDao.delete(session)
    suspend fun getSessionById(id: Long): SleepSession? = sessionDao.getById(id)
    fun getSessionByIdFlow(id: Long): Flow<SleepSession?> = sessionDao.getByIdFlow(id)
    fun getAllSessions(): Flow<List<SleepSession>> = sessionDao.getAllSessions()
    suspend fun getSessionByDate(date: String): SleepSession? = sessionDao.getByDate(date)
    fun getSessionByDateFlow(date: String): Flow<SleepSession?> = sessionDao.getByDateFlow(date)
    fun getSessionsBetween(start: String, end: String): Flow<List<SleepSession>> = sessionDao.getSessionsBetween(start, end)
    suspend fun getActiveSession(): SleepSession? = sessionDao.getActiveSession()
    fun getMostRecentCompletedSession(): Flow<SleepSession?> = sessionDao.getMostRecentCompleted()
    fun getAverageScore(start: String, end: String): Flow<Float?> = sessionDao.getAverageScore(start, end)
    fun getAverageDuration(start: String, end: String): Flow<Float?> = sessionDao.getAverageDuration(start, end)
    fun getRecentSessions(limit: Int = 7): Flow<List<SleepSession>> = sessionDao.getRecentSessions(limit)
    fun getTotalSessionCount(): Flow<Int> = sessionDao.getTotalSessionCount()

    // Sleep Notes
    suspend fun insertNote(note: SleepNote): Long = noteDao.insert(note)
    suspend fun updateNote(note: SleepNote) = noteDao.update(note)
    suspend fun deleteNote(note: SleepNote) = noteDao.delete(note)
    fun getNotesByDate(date: String): Flow<List<SleepNote>> = noteDao.getNotesByDate(date)
    fun getNotesBySession(sessionId: Long): Flow<List<SleepNote>> = noteDao.getNotesBySession(sessionId)
    fun getAllNotes(): Flow<List<SleepNote>> = noteDao.getAllNotes()
    fun getNotesByTag(tag: String): Flow<List<SleepNote>> = noteDao.getNotesByTag(tag)

    // Alarms
    suspend fun insertAlarm(alarm: AlarmConfig): Long = alarmDao.insert(alarm)
    suspend fun updateAlarm(alarm: AlarmConfig) = alarmDao.update(alarm)
    suspend fun deleteAlarm(alarm: AlarmConfig) = alarmDao.delete(alarm)
    suspend fun getAlarmById(id: Long): AlarmConfig? = alarmDao.getById(id)
    fun getAllAlarms(): Flow<List<AlarmConfig>> = alarmDao.getAllAlarms()
    fun getEnabledAlarms(): Flow<List<AlarmConfig>> = alarmDao.getEnabledAlarms()
    suspend fun getEnabledAlarmsList(): List<AlarmConfig> = alarmDao.getEnabledAlarmsList()

    // Audio Recordings
    suspend fun insertRecording(recording: AudioRecording): Long = recordingDao.insert(recording)
    suspend fun updateRecording(recording: AudioRecording) = recordingDao.update(recording)
    suspend fun deleteRecording(recording: AudioRecording) = recordingDao.delete(recording)
    fun getRecordingsBySession(sessionId: Long): Flow<List<AudioRecording>> = recordingDao.getBySession(sessionId)
    fun getRecordingsByDate(date: String): Flow<List<AudioRecording>> = recordingDao.getByDate(date)
    fun getAllRecordings(): Flow<List<AudioRecording>> = recordingDao.getAllRecordings()
    fun getRecordingsByType(type: String): Flow<List<AudioRecording>> = recordingDao.getByType(type)
    fun getRecordingCountByType(sessionId: Long, type: String): Flow<Int> = recordingDao.getCountByType(sessionId, type)
    fun getRecentRecordings(limit: Int = 20): Flow<List<AudioRecording>> = recordingDao.getRecentRecordings(limit)
    suspend fun getPendingTranscriptions(): List<AudioRecording> = recordingDao.getPendingTranscriptions()
    suspend fun updateRecordingTranscript(id: Long, transcript: String) = recordingDao.updateTranscript(id, transcript)

    // Sleep Goals
    suspend fun insertGoal(goal: SleepGoal): Long = goalDao.insert(goal)
    suspend fun updateGoal(goal: SleepGoal) = goalDao.update(goal)
    suspend fun deleteGoal(goal: SleepGoal) = goalDao.delete(goal)
    fun getActiveGoal(): Flow<SleepGoal?> = goalDao.getActiveGoal()
    suspend fun getActiveGoalSync(): SleepGoal? = goalDao.getActiveGoalSync()
    fun getAllGoals(): Flow<List<SleepGoal>> = goalDao.getAllGoals()

    // Sleep Programs
    suspend fun insertProgram(program: SleepProgram): Long = programDao.insert(program)
    suspend fun updateProgram(program: SleepProgram) = programDao.update(program)
    suspend fun deleteProgram(program: SleepProgram) = programDao.delete(program)
    suspend fun getProgramById(id: Long): SleepProgram? = programDao.getById(id)
    fun getAllPrograms(): Flow<List<SleepProgram>> = programDao.getAllPrograms()
    fun getActivePrograms(): Flow<List<SleepProgram>> = programDao.getActivePrograms()
    fun getProgramsByCategory(category: String): Flow<List<SleepProgram>> = programDao.getByCategory(category)

    // Voice Profiles
    suspend fun insertVoiceProfile(profile: VoiceProfile): Long = voiceProfileDao.insert(profile)
    suspend fun updateVoiceProfile(profile: VoiceProfile) = voiceProfileDao.update(profile)
    suspend fun deleteVoiceProfile(profile: VoiceProfile) = voiceProfileDao.delete(profile)
    suspend fun getActiveVoiceProfile(): VoiceProfile? = voiceProfileDao.getActive()
    fun getActiveVoiceProfileFlow(): kotlinx.coroutines.flow.Flow<VoiceProfile?> = voiceProfileDao.getActiveFlow()
    fun getAllVoiceProfiles(): kotlinx.coroutines.flow.Flow<List<VoiceProfile>> = voiceProfileDao.getAll()
    suspend fun deactivateAllVoiceProfiles() = voiceProfileDao.deactivateAll()
    suspend fun replaceVoiceProfile(profile: VoiceProfile): Long {
        voiceProfileDao.deactivateAll()
        return voiceProfileDao.insert(profile.copy(isActive = true))
    }

    // User Profile
    private val userProfileDao = db.userProfileDao()
    suspend fun getUserProfile(): UserProfile? = userProfileDao.get()
    fun observeUserProfile(): Flow<UserProfile?> = userProfileDao.observe()
    suspend fun upsertUserProfile(profile: UserProfile) =
        userProfileDao.upsert(profile.copy(id = UserProfile.SINGLETON_ID, updatedAt = System.currentTimeMillis()))

    // User Account
    private val userAccountDao = db.userAccountDao()
    suspend fun getUserAccount(): UserAccount? = userAccountDao.get()
    fun observeUserAccount(): Flow<UserAccount?> = userAccountDao.observe()
    suspend fun upsertUserAccount(account: UserAccount) =
        userAccountDao.upsert(account.copy(id = UserAccount.SINGLETON_ID))
    suspend fun clearUserAccount() = userAccountDao.clear()

    // Wearable Samples
    private val wearableSampleDao = db.wearableSampleDao()
    private val wearableSleepStageDao = db.wearableSleepStageDao()
    suspend fun insertWearableSamples(samples: List<WearableSample>) {
        if (samples.isNotEmpty()) wearableSampleDao.insertAll(samples)
    }
    suspend fun getWearableSamplesInRange(startMs: Long, endMs: Long, metric: String): List<WearableSample> =
        wearableSampleDao.getInRange(startMs, endMs, metric)
    suspend fun getLatestWearableTimestamp(metric: String): Long? =
        wearableSampleDao.getLatestTimestamp(metric)
    suspend fun getLatestRestingHrBpm(): Float? =
        wearableSampleDao.getLatestValue(WearableMetric.RESTING_HEART_RATE.name)
    fun observeWearableSamples(sessionId: Long, metric: String): Flow<List<WearableSample>> =
        wearableSampleDao.observeBySession(sessionId, metric)
    suspend fun pruneWearableSamplesOlderThan(cutoffMs: Long): Int =
        wearableSampleDao.deleteOlderThan(cutoffMs)

    suspend fun insertVendorStageSegments(rows: List<WearableSleepStage>): Int {
        if (rows.isEmpty()) return 0
        return wearableSleepStageDao.insertAll(rows).count { it != -1L }
    }

    suspend fun getVendorStageSegmentsInRange(sinceMs: Long, untilMs: Long): List<WearableSleepStage> =
        wearableSleepStageDao.getInRange(sinceMs, untilMs)

    suspend fun pruneVendorStageSegments(cutoffMs: Long): Int =
        wearableSleepStageDao.deleteOlderThan(cutoffMs)

    suspend fun getLatestVendorStageEndMs(): Long? =
        wearableSleepStageDao.getLatestEndTimeMs()

    suspend fun getVendorStageSegmentsForSession(start: Long, end: Long): List<VendorStageSegment> =
        getVendorStageSegmentsInRange(start, end).map { row ->
            VendorStageSegment(
                startMs = row.startTime,
                endMs = row.endTime,
                stage = SleepStage.fromKey(row.stage) ?: SleepStage.LIGHT,
                sourceProvider = row.sourceProvider,
                deviceId = row.deviceId
            )
        }

    // Wearable Devices
    private val wearableDeviceDao = db.wearableDeviceDao()
    suspend fun upsertWearableDevice(device: WearableDevice) = wearableDeviceDao.upsert(device)
    suspend fun getWearableDevice(id: String): WearableDevice? = wearableDeviceDao.getById(id)
    fun observeActiveWearableDevices(): Flow<List<WearableDevice>> = wearableDeviceDao.observeActive()
    fun observeAllWearableDevices(): Flow<List<WearableDevice>> = wearableDeviceDao.observeAll()
    suspend fun markWearableSynced(id: String, nowMs: Long = System.currentTimeMillis()) =
        wearableDeviceDao.markSynced(id, nowMs)
}
