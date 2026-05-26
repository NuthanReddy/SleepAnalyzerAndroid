package tech.future.sleepanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_account")
data class UserAccount(
    @PrimaryKey val id: Long = 1L,
    val uid: String? = null,
    /** Primary identifier when signed in via phone. E.164 (e.g. "+14155552671"). */
    val phoneNumber: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val provider: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncMs: Long = 0L,
    val syncEnabled: Boolean = false
) {
    companion object {
        const val SINGLETON_ID = 1L
        fun empty() = UserAccount()
    }
}
