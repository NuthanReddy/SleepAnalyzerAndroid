package tech.future.sleepanalyzer.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.data.db.entity.AudioRecording

@Dao
interface AudioRecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: AudioRecording): Long

    @Update
    suspend fun update(recording: AudioRecording)

    @Delete
    suspend fun delete(recording: AudioRecording)

    @Query("SELECT * FROM audio_recordings WHERE sessionId = :sessionId ORDER BY startTime ASC")
    fun getBySession(sessionId: Long): Flow<List<AudioRecording>>

    @Query("SELECT * FROM audio_recordings WHERE date = :date ORDER BY startTime ASC")
    fun getByDate(date: String): Flow<List<AudioRecording>>

    @Query("SELECT * FROM audio_recordings ORDER BY startTime DESC")
    fun getAllRecordings(): Flow<List<AudioRecording>>

    @Query("SELECT * FROM audio_recordings WHERE type = :type ORDER BY startTime DESC")
    fun getByType(type: String): Flow<List<AudioRecording>>

    @Query("SELECT COUNT(*) FROM audio_recordings WHERE sessionId = :sessionId AND type = :type")
    fun getCountByType(sessionId: Long, type: String): Flow<Int>

    @Query("SELECT * FROM audio_recordings ORDER BY startTime DESC LIMIT :limit")
    fun getRecentRecordings(limit: Int = 20): Flow<List<AudioRecording>>
}
