package tech.future.sleepanalyzer.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.data.db.entity.SleepSession

@Dao
interface SleepSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SleepSession): Long

    @Update
    suspend fun update(session: SleepSession)

    @Delete
    suspend fun delete(session: SleepSession)

    @Query("SELECT * FROM sleep_sessions WHERE id = :id")
    suspend fun getById(id: Long): SleepSession?

    @Query("SELECT * FROM sleep_sessions WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<SleepSession?>

    @Query("SELECT * FROM sleep_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_sessions WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): SleepSession?

    @Query("SELECT * FROM sleep_sessions WHERE date = :date LIMIT 1")
    fun getByDateFlow(date: String): Flow<SleepSession?>

    @Query("SELECT * FROM sleep_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getSessionsBetween(startDate: String, endDate: String): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_sessions WHERE isTracking = 0 AND qualityScore > 0 ORDER BY endTime DESC LIMIT 1")
    fun getMostRecentCompleted(): Flow<SleepSession?>

    @Query("SELECT * FROM sleep_sessions WHERE isTracking = 1 LIMIT 1")
    suspend fun getActiveSession(): SleepSession?

    @Query("SELECT * FROM sleep_sessions WHERE isTracking = 1 ORDER BY startTime DESC")
    suspend fun getActiveSessions(): List<SleepSession>

    @Query("SELECT AVG(qualityScore) FROM sleep_sessions WHERE date BETWEEN :startDate AND :endDate AND qualityScore > 0")
    fun getAverageScore(startDate: String, endDate: String): Flow<Float?>

    @Query("SELECT AVG(durationMinutes) FROM sleep_sessions WHERE date BETWEEN :startDate AND :endDate AND durationMinutes > 0")
    fun getAverageDuration(startDate: String, endDate: String): Flow<Float?>

    @Query("SELECT * FROM sleep_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 7): Flow<List<SleepSession>>

    @Query("SELECT COUNT(*) FROM sleep_sessions WHERE qualityScore > 0")
    fun getTotalSessionCount(): Flow<Int>
}
