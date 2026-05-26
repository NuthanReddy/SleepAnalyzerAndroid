package tech.future.sleepanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tech.future.sleepanalyzer.data.db.entity.UserAccount

@Dao
interface UserAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: UserAccount)

    @Query("SELECT * FROM user_account WHERE id = 1 LIMIT 1")
    suspend fun get(): UserAccount?

    @Query("SELECT * FROM user_account WHERE id = 1 LIMIT 1")
    fun observe(): Flow<UserAccount?>

    @Query("DELETE FROM user_account")
    suspend fun clear()
}
