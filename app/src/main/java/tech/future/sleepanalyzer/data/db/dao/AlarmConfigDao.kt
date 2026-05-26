package tech.future.sleepanalyzer.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.data.db.entity.AlarmConfig

@Dao
interface AlarmConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmConfig): Long

    @Update
    suspend fun update(alarm: AlarmConfig)

    @Delete
    suspend fun delete(alarm: AlarmConfig)

    @Query("SELECT * FROM alarm_configs WHERE id = :id")
    suspend fun getById(id: Long): AlarmConfig?

    @Query("SELECT * FROM alarm_configs ORDER BY hour ASC, minute ASC")
    fun getAllAlarms(): Flow<List<AlarmConfig>>

    @Query("SELECT * FROM alarm_configs WHERE isEnabled = 1")
    fun getEnabledAlarms(): Flow<List<AlarmConfig>>

    @Query("SELECT * FROM alarm_configs WHERE isEnabled = 1")
    suspend fun getEnabledAlarmsList(): List<AlarmConfig>
}
