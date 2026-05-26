package tech.future.sleepanalyzer.ui.auth

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.auth.FIREBASE_NOT_CONFIGURED_MESSAGE
import tech.future.sleepanalyzer.auth.GoogleSignInCoordinator
import tech.future.sleepanalyzer.auth.PhoneAuthCoordinator
import tech.future.sleepanalyzer.auth.resolveDefaultWebClientId
import tech.future.sleepanalyzer.di.ServiceLocator

class SignupViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository
        get() = ServiceLocator.authRepository

    private val phoneAuthCoordinator = PhoneAuthCoordinator()
    private val googleSignInCoordinator = GoogleSignInCoordinator()

    private val _signupState = MutableStateFlow<SignupUiState>(SignupUiState.Idle)
    val signupState: StateFlow<SignupUiState> = _signupState.asStateFlow()

    val isConfigured: Boolean
        get() = authRepository.isConfigured()

    fun startPhoneVerification(activity: Activity, phoneE164: String) {
        if (!isConfigured) {
            _signupState.value = SignupUiState.NotConfigured()
            return
        }
        viewModelScope.launch {
            _signupState.value = SignupUiState.Loading
            authRepository.setVerifying(phoneE164)
            when (val result = phoneAuthCoordinator.startVerification(activity, phoneE164)) {
                is PhoneAuthCoordinator.VerificationResult.CodeSent -> {
                    _signupState.value = SignupUiState.AwaitingCode(phoneE164, result.verificationId)
                }
                is PhoneAuthCoordinator.VerificationResult.AutoVerified -> {
                    phoneAuthCoordinator.signInWithCredential(result.credential)
                        .onSuccess { user ->
                            authRepository.onSignedIn(user, "phone")
                            _signupState.value = SignupUiState.SignedIn("phone")
                        }
                        .onFailure { error ->
                            authRepository.setError(error.message ?: "Phone sign-in failed.")
                            _signupState.value = SignupUiState.Error(error.message ?: "Phone sign-in failed.")
                        }
                }
                is PhoneAuthCoordinator.VerificationResult.Failed -> {
                    authRepository.setError(result.message)
                    _signupState.value = SignupUiState.Error(result.message)
                }
            }
        }
    }

    fun confirmPhoneCode(code: String) {
        val state = _signupState.value as? SignupUiState.AwaitingCode ?: return
        if (!isConfigured) {
            _signupState.value = SignupUiState.NotConfigured()
            return
        }
        viewModelScope.launch {
            _signupState.value = SignupUiState.Loading
            authRepository.setVerifying(state.phoneNumber)
            phoneAuthCoordinator.verifyCode(state.verificationId, code)
                .onSuccess { user ->
                    authRepository.onSignedIn(user, "phone")
                    _signupState.value = SignupUiState.SignedIn("phone")
                }
                .onFailure { error ->
                    authRepository.setError(error.message ?: "Invalid verification code.")
                    _signupState.value = SignupUiState.Error(error.message ?: "Invalid verification code.")
                }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        if (!isConfigured) {
            _signupState.value = SignupUiState.NotConfigured()
            return
        }
        viewModelScope.launch {
            _signupState.value = SignupUiState.Loading
            authRepository.setVerifying(null)
            val serverClientId = runCatching { resolveDefaultWebClientId(activity) }
                .getOrElse {
                    authRepository.setError(FIREBASE_NOT_CONFIGURED_MESSAGE)
                    _signupState.value = SignupUiState.NotConfigured()
                    return@launch
                }
            googleSignInCoordinator.signIn(activity, serverClientId)
                .onSuccess { user ->
                    authRepository.onSignedIn(user, "google")
                    _signupState.value = SignupUiState.SignedIn("google")
                }
                .onFailure { error ->
                    authRepository.setError(error.message ?: "Google sign-in failed.")
                    _signupState.value = SignupUiState.Error(error.message ?: "Google sign-in failed.")
                }
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            authRepository.continueAsGuest()
            _signupState.value = SignupUiState.Guest
        }
    }

    /**
     * Create a fully on-device account. Used when Firebase isn't configured, or when the user
     * prefers a name-only signup over phone/Google. The user is still signed in (provider = "local"),
     * so onboarding can finish and profile/sessions/notes attach to a stable uid.
     */
    fun signUpLocal(displayName: String, email: String?) {
        val trimmedName = displayName.trim()
        if (trimmedName.isEmpty()) {
            _signupState.value = SignupUiState.Error("Enter a display name to continue.")
            return
        }
        viewModelScope.launch {
            _signupState.value = SignupUiState.Loading
            runCatching {
                authRepository.signUpLocal(trimmedName, email)
            }.onSuccess {
                _signupState.value = SignupUiState.SignedIn("local")
            }.onFailure { error ->
                val message = error.message ?: "Unable to create local account."
                authRepository.setError(message)
                _signupState.value = SignupUiState.Error(message)
            }
        }
    }

    fun clearSignupState() {
        _signupState.value = SignupUiState.Idle
    }
}
