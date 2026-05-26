package tech.future.sleepanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.data.db.entity.UserProfile

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)

    @Update
    suspend fun update(profile: UserProfile)

    @Delete
    suspend fun delete(profile: UserProfile)

    @Query("SELECT * FROM user_profile WHERE id = :id LIMIT 1")
    suspend fun get(id: Long = UserProfile.SINGLETON_ID): UserProfile?

    @Query("SELECT * FROM user_profile WHERE id = :id LIMIT 1")
    fun observe(id: Long = UserProfile.SINGLETON_ID): Flow<UserProfile?>
}
