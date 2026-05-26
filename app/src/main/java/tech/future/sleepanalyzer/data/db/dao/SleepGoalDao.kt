package tech.future.sleepanalyzer.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.data.db.entity.SleepGoal

@Dao
interface SleepGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SleepGoal): Long

    @Update
    suspend fun update(goal: SleepGoal)

    @Delete
    suspend fun delete(goal: SleepGoal)

    @Query("SELECT * FROM sleep_goals WHERE isActive = 1 LIMIT 1")
    fun getActiveGoal(): Flow<SleepGoal?>

    @Query("SELECT * FROM sleep_goals WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveGoalSync(): SleepGoal?

    @Query("SELECT * FROM sleep_goals ORDER BY createdAt DESC")
    fun getAllGoals(): Flow<List<SleepGoal>>
}
