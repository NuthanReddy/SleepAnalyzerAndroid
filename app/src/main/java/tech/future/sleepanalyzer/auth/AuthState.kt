package tech.future.sleepanalyzer.auth

sealed interface AuthState {
    data object NotConfigured : AuthState
    data object Guest : AuthState
    data class Verifying(val phoneNumber: String? = null) : AuthState
    data class SignedIn(
        val uid: String,
        val phoneNumber: String?,
        val email: String?,
        val displayName: String?,
        val photoUrl: String?,
        val provider: String
    ) : AuthState

    data class Error(val message: String) : AuthState
}
