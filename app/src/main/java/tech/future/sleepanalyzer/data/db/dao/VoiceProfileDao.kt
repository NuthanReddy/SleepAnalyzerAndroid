package tech.future.sleepanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.data.db.entity.VoiceProfile

@Dao
interface VoiceProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: VoiceProfile): Long

    @Update
    suspend fun update(profile: VoiceProfile)

    @Delete
    suspend fun delete(profile: VoiceProfile)

    @Query("SELECT * FROM voice_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): VoiceProfile?

    @Query("SELECT * FROM voice_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveFlow(): Flow<VoiceProfile?>

    @Query("SELECT * FROM voice_profiles ORDER BY createdAt DESC")
    fun getAll(): Flow<List<VoiceProfile>>

    @Query("UPDATE voice_profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Query("DELETE FROM voice_profiles")
    suspend fun deleteAll()
}
