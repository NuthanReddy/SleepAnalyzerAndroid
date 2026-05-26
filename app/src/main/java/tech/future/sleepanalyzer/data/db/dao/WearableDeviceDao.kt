package tech.future.sleepanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.data.db.entity.WearableDevice

@Dao
interface WearableDeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: WearableDevice)

    @Update
    suspend fun update(device: WearableDevice)

    @Delete
    suspend fun delete(device: WearableDevice)

    @Query("SELECT * FROM wearable_devices WHERE isActive = 1 ORDER BY lastSyncMs DESC")
    fun observeActive(): Flow<List<WearableDevice>>

    @Query("SELECT * FROM wearable_devices ORDER BY displayName ASC")
    fun observeAll(): Flow<List<WearableDevice>>

    @Query("SELECT * FROM wearable_devices WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WearableDevice?

    @Query("UPDATE wearable_devices SET lastSyncMs = :nowMs WHERE id = :id")
    suspend fun markSynced(id: String, nowMs: Long)
}
