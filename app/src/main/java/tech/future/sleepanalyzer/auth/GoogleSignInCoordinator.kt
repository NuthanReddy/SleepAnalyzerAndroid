package tech.future.sleepanalyzer.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import tech.future.sleepanalyzer.util.awaitValue

class GoogleSignInCoordinator {
    suspend fun signIn(context: Context, serverClientId: String): Result<FirebaseUser> {
        val firebaseAuth = firebaseAuthOrNull() ?: return Result.failure(firebaseNotConfiguredException())
        return runCatching {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(serverClientId)
                .setFilterByAuthorizedAccounts(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            val customCredential = credential as? CustomCredential
                ?: error("Google sign-in was cancelled")
            if (customCredential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                error("Unsupported Google sign-in credential")
            }
            val googleTokenCredential = GoogleIdTokenCredential.createFrom(customCredential.data)
            val authCredential = GoogleAuthProvider.getCredential(googleTokenCredential.idToken, null)
            val authResult = firebaseAuth.signInWithCredential(authCredential).awaitValue()
            authResult.user ?: error("Google sign-in completed without a Firebase user")
        }
    }
}
