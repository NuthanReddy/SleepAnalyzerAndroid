package tech.future.sleepanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tech.future.sleepanalyzer.data.db.entity.WearableSleepStage

@Dao
interface WearableSleepStageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<WearableSleepStage>): List<Long>

    @Query("SELECT * FROM wearable_sleep_stages WHERE endTime > :sinceMs AND startTime < :untilMs ORDER BY startTime ASC")
    suspend fun getInRange(sinceMs: Long, untilMs: Long): List<WearableSleepStage>

    @Query("SELECT MAX(endTime) FROM wearable_sleep_stages")
    suspend fun getLatestEndTimeMs(): Long?

    @Query("DELETE FROM wearable_sleep_stages WHERE endTime < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}
