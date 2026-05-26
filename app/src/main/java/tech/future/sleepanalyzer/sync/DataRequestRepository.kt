package tech.future.sleepanalyzer.sync

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import tech.future.sleepanalyzer.auth.AuthRepository
import tech.future.sleepanalyzer.auth.firebaseNotConfiguredException
import tech.future.sleepanalyzer.auth.isPlaceholderFirebase
import tech.future.sleepanalyzer.data.db.entity.UserAccount
import tech.future.sleepanalyzer.data.prefs.AppPreferences
import tech.future.sleepanalyzer.util.awaitCompletion

class DataRequestRepository(
    private val authRepository: AuthRepository,
    private val preferences: AppPreferences
) {
    suspend fun submitExportRequest(account: UserAccount): Result<String> =
        submitRequest(account, "export")

    suspend fun submitDeleteRequest(account: UserAccount): Result<String> =
        submitRequest(account, "delete")

    private suspend fun submitRequest(account: UserAccount, type: String): Result<String> = runCatching {
        if (!authRepository.isConfigured()) throw firebaseNotConfiguredException()
        val uid = account.uid?.takeIf { it.isNotBlank() } ?: throw firebaseNotConfiguredException(
            "Sign in to submit a data request."
        )
        val app = FirebaseApp.getInstance()
        if (isPlaceholderFirebase(app)) throw firebaseNotConfiguredException()
        val firestore = FirebaseFirestore.getInstance(app)
        val doc = firestore.collection("data_requests").document()
        doc.set(
            mapOf(
                "uid" to uid,
                "phoneNumber" to account.phoneNumber,
                "email" to account.email,
                "type" to type,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).awaitCompletion()
        preferences.setLastDataRequestId(doc.id)
        doc.id
    }
}
