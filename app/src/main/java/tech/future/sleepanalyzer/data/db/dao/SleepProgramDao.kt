package tech.future.sleepanalyzer.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.data.db.entity.SleepProgram

@Dao
interface SleepProgramDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(program: SleepProgram): Long

    @Update
    suspend fun update(program: SleepProgram)

    @Delete
    suspend fun delete(program: SleepProgram)

    @Query("SELECT * FROM sleep_programs WHERE id = :id")
    suspend fun getById(id: Long): SleepProgram?

    @Query("SELECT * FROM sleep_programs ORDER BY title ASC")
    fun getAllPrograms(): Flow<List<SleepProgram>>

    @Query("SELECT * FROM sleep_programs WHERE isStarted = 1 AND isCompleted = 0")
    fun getActivePrograms(): Flow<List<SleepProgram>>

    @Query("SELECT * FROM sleep_programs WHERE category = :category")
    fun getByCategory(category: String): Flow<List<SleepProgram>>
}
