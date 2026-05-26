package tech.future.sleepanalyzer.ui.auth

import tech.future.sleepanalyzer.auth.FIREBASE_NOT_CONFIGURED_MESSAGE

sealed interface SignupUiState {
    data object Idle : SignupUiState
    data object Loading : SignupUiState
    data class AwaitingCode(
        val phoneNumber: String,
        val verificationId: String
    ) : SignupUiState

    data class SignedIn(val provider: String) : SignupUiState
    data object Guest : SignupUiState
    data class Error(val message: String) : SignupUiState
    data class NotConfigured(
        val message: String = FIREBASE_NOT_CONFIGURED_MESSAGE
    ) : SignupUiState
}
