package tech.future.sleepanalyzer.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.data.db.entity.SleepNote

@Dao
interface SleepNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: SleepNote): Long

    @Update
    suspend fun update(note: SleepNote)

    @Delete
    suspend fun delete(note: SleepNote)

    @Query("SELECT * FROM sleep_notes WHERE date = :date")
    fun getNotesByDate(date: String): Flow<List<SleepNote>>

    @Query("SELECT * FROM sleep_notes WHERE sessionId = :sessionId")
    fun getNotesBySession(sessionId: Long): Flow<List<SleepNote>>

    @Query("SELECT * FROM sleep_notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<SleepNote>>

    @Query("SELECT * FROM sleep_notes WHERE tags LIKE '%' || :tag || '%' ORDER BY createdAt DESC")
    fun getNotesByTag(tag: String): Flow<List<SleepNote>>
}
