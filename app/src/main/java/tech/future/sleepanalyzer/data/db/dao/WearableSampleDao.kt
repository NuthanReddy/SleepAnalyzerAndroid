package tech.future.sleepanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.data.db.entity.WearableSample

@Dao
interface WearableSampleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sample: WearableSample): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(samples: List<WearableSample>): List<Long>

    @Query("SELECT * FROM wearable_samples WHERE sessionId = :sessionId AND metric = :metric ORDER BY timestamp ASC")
    fun observeBySession(sessionId: Long, metric: String): Flow<List<WearableSample>>

    @Query("SELECT * FROM wearable_samples WHERE timestamp BETWEEN :startMs AND :endMs AND metric = :metric ORDER BY timestamp ASC")
    suspend fun getInRange(startMs: Long, endMs: Long, metric: String): List<WearableSample>

    @Query("SELECT MAX(timestamp) FROM wearable_samples WHERE metric = :metric")
    suspend fun getLatestTimestamp(metric: String): Long?

    @Query("SELECT value FROM wearable_samples WHERE metric = :metric ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestValue(metric: String): Float?

    @Query("DELETE FROM wearable_samples WHERE timestamp < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}
