package tech.future.sleepanalyzer.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import tech.future.sleepanalyzer.auth.AuthRepository
import tech.future.sleepanalyzer.auth.firebaseNotConfiguredException
import tech.future.sleepanalyzer.auth.isPlaceholderFirebase
import tech.future.sleepanalyzer.data.db.entity.AudioRecording
import tech.future.sleepanalyzer.data.db.entity.SleepGoal
import tech.future.sleepanalyzer.data.db.entity.SleepNote
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.db.entity.UserProfile
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.util.awaitCompletion

class SyncRepository(
    context: Context,
    private val repository: SleepRepository,
    private val authRepository: AuthRepository
) {
    @Suppress("unused")
    private val appContext = context.applicationContext

    suspend fun uploadFullSnapshot(uid: String): Result<Int> = runCatching {
        val firestore = firestore().getOrThrow()
        val sessions = repository.getAllSessions().first()
        val recordings = repository.getAllRecordings().first()
        val notes = repository.getAllNotes().first()
        val dates = (sessions.map { it.date } + recordings.map { it.date } + notes.map { it.date })
            .filter { it.isNotBlank() }
            .toSet()
            .sorted()
        val writes = buildList {
            repository.getUserProfile()?.let { add(profileWrite(firestore, uid, it)) }
            repository.getActiveGoalSync()?.let { add(goalWrite(firestore, uid, it)) }
            dates.forEach { add(dailySummaryWrite(firestore, uid, computeDailySummary(it))) }
            sessions.forEach { add(sessionWrite(firestore, uid, it)) }
            recordings.forEach { add(audioEventWrite(firestore, uid, audioEventDoc(it))) }
            notes.forEach { add(noteWrite(firestore, uid, noteDoc(it))) }
        }
        commitWrites(firestore, writes)
    }

    suspend fun uploadIncremental(uid: String, sinceMs: Long): Result<Int> {
        if (sinceMs <= 0L) return uploadFullSnapshot(uid)
        return runCatching {
            val firestore = firestore().getOrThrow()
            val sessions = repository.getAllSessions().first().filter {
                maxOf(it.startTime, it.endTime ?: it.startTime) >= sinceMs
            }
            val recordings = repository.getAllRecordings().first().filter {
                maxOf(it.startTime, it.endTime) >= sinceMs
            }
            val notes = repository.getAllNotes().first().filter { it.createdAt >= sinceMs }
            val activeGoal = repository.getActiveGoalSync()?.takeIf { it.createdAt >= sinceMs }
            val profile = repository.getUserProfile()?.takeIf { it.updatedAt >= sinceMs }
            val dates = (sessions.map { it.date } + recordings.map { it.date } + notes.map { it.date })
                .filter { it.isNotBlank() }
                .toSet()
                .sorted()
            val writes = buildList {
                profile?.let { add(profileWrite(firestore, uid, it)) }
                activeGoal?.let { add(goalWrite(firestore, uid, it)) }
                dates.forEach { add(dailySummaryWrite(firestore, uid, computeDailySummary(it))) }
                sessions.forEach { add(sessionWrite(firestore, uid, it)) }
                recordings.forEach { add(audioEventWrite(firestore, uid, audioEventDoc(it))) }
                notes.forEach { add(noteWrite(firestore, uid, noteDoc(it))) }
            }
            commitWrites(firestore, writes)
        }
    }

    suspend fun computeDailySummary(date: String): DailySummaryDoc {
        val sessions = repository.getSessionsBetween(date, date).first()
        val recordings = repository.getRecordingsByDate(date).first()
        val qualityScore = sessions.map { it.qualityScore }.filter { it > 0 }.let {
            if (it.isEmpty()) 0 else it.sum() / it.size
        }
        return DailySummaryDoc(
            date = date,
            qualityScore = qualityScore,
            durationMinutes = sessions.sumOf { it.durationMinutes },
            deepSleepMinutes = sessions.sumOf { it.deepSleepMinutes },
            lightSleepMinutes = sessions.sumOf { it.lightSleepMinutes },
            remSleepMinutes = sessions.sumOf { it.remSleepMinutes },
            awakeMinutes = sessions.sumOf { it.awakeMinutes },
            interruptions = sessions.sumOf { it.interruptions },
            snoreEvents = recordings.count { it.type == "snore" },
            coughEvents = recordings.count { it.type == "cough" },
            talkEvents = recordings.count { it.type == "talk" },
            attributionSummary = recordings.groupingBy { it.attributedTo }.eachCount().toSortedMap()
        )
    }

    private fun firestore(): Result<FirebaseFirestore> = runCatching {
        if (!authRepository.isConfigured()) throw firebaseNotConfiguredException()
        val app = FirebaseApp.getInstance()
        if (isPlaceholderFirebase(app)) throw firebaseNotConfiguredException()
        FirebaseFirestore.getInstance(app)
    }

    private suspend fun commitWrites(
        firestore: FirebaseFirestore,
        writes: List<Pair<DocumentReference, Any>>
    ): Int {
        if (writes.isEmpty()) {
            updateLastSync()
            return 0
        }
        writes.chunked(MAX_BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (reference, value) ->
                batch.set(reference, value)
            }
            batch.commit().awaitCompletion()
        }
        updateLastSync()
        return writes.size
    }

    private suspend fun updateLastSync() {
        val now = System.currentTimeMillis()
        repository.getUserAccount()?.let { account ->
            repository.upsertUserAccount(account.copy(lastSyncMs = now))
        }
    }

    private fun profileWrite(
        firestore: FirebaseFirestore,
        uid: String,
        profile: UserProfile
    ): Pair<DocumentReference, Any> = firestore.document("users/$uid/profile/main") to ProfileDoc(
        uid = uid,
        displayName = profile.displayName,
        dateOfBirth = profile.dateOfBirth,
        biologicalSex = profile.biologicalSex,
        heightCm = profile.heightCm,
        weightKg = profile.weightKg,
        activityLevel = profile.activityLevel,
        units = profile.units,
        sleepConditions = profile.conditionsList,
        medications = profile.medications,
        typicalCaffeineCutoffHour = profile.typicalCaffeineCutoffHour,
        shiftWorkSchedule = profile.shiftWorkSchedule,
        updatedAt = profile.updatedAt
    )

    private fun goalWrite(
        firestore: FirebaseFirestore,
        uid: String,
        goal: SleepGoal
    ): Pair<DocumentReference, Any> = firestore.document("users/$uid/goals/active") to GoalDoc(
        goalId = goal.id,
        targetBedtimeHour = goal.targetBedtimeHour,
        targetBedtimeMinute = goal.targetBedtimeMinute,
        targetWakeHour = goal.targetWakeHour,
        targetWakeMinute = goal.targetWakeMinute,
        targetDurationMinutes = goal.targetDurationMinutes,
        targetScore = goal.targetScore,
        isActive = goal.isActive,
        updatedAt = goal.createdAt
    )

    private fun sessionWrite(
        firestore: FirebaseFirestore,
        uid: String,
        session: SleepSession
    ): Pair<DocumentReference, Any> = firestore.document("users/$uid/sessions/${session.id}") to SessionDoc(
        sessionId = session.id,
        startTime = session.startTime,
        endTime = session.endTime,
        durationMinutes = session.durationMinutes,
        qualityScore = session.qualityScore,
        stagesBreakdown = mapOf(
            "deep" to session.deepSleepMinutes,
            "light" to session.lightSleepMinutes,
            "rem" to session.remSleepMinutes,
            "awake" to session.awakeMinutes
        ),
        moodBefore = session.moodBefore,
        moodAfter = session.moodAfter,
        date = session.date
    )

    private fun dailySummaryWrite(
        firestore: FirebaseFirestore,
        uid: String,
        summary: DailySummaryDoc
    ): Pair<DocumentReference, Any> =
        firestore.document("users/$uid/daily_summary/${summary.date}") to summary

    private fun audioEventWrite(
        firestore: FirebaseFirestore,
        uid: String,
        event: AudioEventDoc
    ): Pair<DocumentReference, Any> =
        firestore.document("users/$uid/audio_events/${event.recordingId}") to event

    private fun noteWrite(
        firestore: FirebaseFirestore,
        uid: String,
        note: NoteDoc
    ): Pair<DocumentReference, Any> =
        firestore.document("users/$uid/notes/${note.noteId}") to note

    private fun noteDoc(note: SleepNote): NoteDoc = NoteDoc(
        noteId = note.id,
        sessionId = note.sessionId,
        date = note.date,
        tags = note.tags.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) },
        note = note.note,
        createdAt = note.createdAt
    )

    private fun audioEventDoc(recording: AudioRecording): AudioEventDoc = AudioEventDoc(
        recordingId = recording.id,
        sessionId = recording.sessionId,
        startTime = recording.startTime,
        endTime = recording.endTime,
        durationSeconds = recording.durationSeconds,
        type = recording.type,
        attributedTo = recording.attributedTo,
        matchConfidence = recording.matchConfidence,
        pitchHz = recording.pitchHz,
        maxAmplitude = recording.maxAmplitude,
        croppedFromMs = recording.croppedFromMs,
        croppedToMs = recording.croppedToMs,
        date = recording.date
    )

    companion object {
        private const val MAX_BATCH_SIZE = 500
    }
}
