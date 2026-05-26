package tech.future.sleepanalyzer.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.data.db.entity.UserAccount
import tech.future.sleepanalyzer.data.prefs.AppPreferences
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.util.awaitCompletion

class AuthRepository(
    context: Context,
    private val repository: SleepRepository,
    private val preferences: AppPreferences
) {
    @Suppress("unused")
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        when {
            user == null && _state.value is AuthState.SignedIn -> {
                scope.launch {
                    repository.clearUserAccount()
                    _state.value = AuthState.Guest
                }
            }
            user != null && isConfigured() -> {
                scope.launch {
                    persistAndEmit(user, providerFrom(user))
                }
            }
        }
    }

    init {
        firebaseAuthOrNull()?.addAuthStateListener(authStateListener)
    }

    fun isConfigured(): Boolean {
        val app = try {
            FirebaseApp.getInstance()
        } catch (_: IllegalStateException) {
            return false
        } catch (_: com.google.android.gms.common.api.ApiException) {
            return false
        }
        return !isPlaceholderFirebase(app)
    }

    fun setVerifying(phoneNumber: String? = null) {
        _state.value = AuthState.Verifying(phoneNumber)
    }

    fun setError(message: String) {
        _state.value = AuthState.Error(message)
    }

    suspend fun signOut() {
        firebaseAuthOrNull()?.signOut()
        repository.clearUserAccount()
        _state.value = AuthState.Guest
    }

    suspend fun continueAsGuest() {
        repository.clearUserAccount()
        _state.value = AuthState.Guest
    }

    /**
     * Create a fully on-device account when Firebase is not configured.
     * The user is still "signed in" (provider = "local") so the rest of the app — profile,
     * goals, sessions, sleep notes — has a stable uid to attach data to. Cloud sync stays off
     * unless / until Firebase is wired up.
     *
     * Returns the created account on success.
     */
    suspend fun signUpLocal(displayName: String, email: String?): UserAccount {
        val existing = repository.getUserAccount()
        val uid = existing?.uid?.takeIf { it.startsWith("local-") }
            ?: ("local-" + java.util.UUID.randomUUID().toString())
        val account = UserAccount(
            id = UserAccount.SINGLETON_ID,
            uid = uid,
            phoneNumber = null,
            email = email?.trim()?.takeIf { it.isNotEmpty() },
            displayName = displayName.trim().ifEmpty { null },
            photoUrl = null,
            provider = "local",
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastSyncMs = 0L,
            syncEnabled = false
        )
        repository.upsertUserAccount(account)
        _state.value = account.toSignedIn()
        return account
    }

    suspend fun onSignedIn(firebaseUser: FirebaseUser, provider: String) {
        persistAndEmit(firebaseUser, provider)
    }

    suspend fun deleteAccount() {
        val auth = firebaseAuthOrNull()
        runCatching {
            auth?.currentUser?.delete()?.awaitCompletion()
        }
        auth?.signOut()
        repository.clearUserAccount()
        _state.value = AuthState.Guest
    }

    private fun initialState(): AuthState {
        if (!isConfigured()) return AuthState.NotConfigured
        val user = firebaseAuthOrNull()?.currentUser ?: return AuthState.Guest
        return user.toSignedIn(providerFrom(user))
    }

    private suspend fun persistAndEmit(firebaseUser: FirebaseUser, provider: String) {
        val existing = repository.getUserAccount()
        val syncEnabled = preferences.cloudSyncEnabledFlow.firstOrNull() ?: existing?.syncEnabled ?: false
        val account = UserAccount(
            id = UserAccount.SINGLETON_ID,
            uid = firebaseUser.uid,
            phoneNumber = firebaseUser.phoneNumber,
            email = firebaseUser.email,
            displayName = firebaseUser.displayName,
            photoUrl = firebaseUser.photoUrl?.toString(),
            provider = provider,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            lastSyncMs = existing?.lastSyncMs ?: 0L,
            syncEnabled = syncEnabled
        )
        repository.upsertUserAccount(account)
        _state.value = account.toSignedIn()
    }
}

private fun providerFrom(user: FirebaseUser): String {
    if (user.isAnonymous) return "anonymous"
    return user.providerData
        .mapNotNull { it.providerId }
        .firstOrNull { it != "firebase" }
        .toProviderKey()
}

private fun String?.toProviderKey(): String = when (this) {
    "phone" -> "phone"
    "google.com" -> "google"
    else -> "anonymous"
}

private fun FirebaseUser.toSignedIn(provider: String): AuthState.SignedIn = AuthState.SignedIn(
    uid = uid,
    phoneNumber = phoneNumber,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl?.toString(),
    provider = provider
)

private fun UserAccount.toSignedIn(): AuthState.SignedIn = AuthState.SignedIn(
    uid = uid.orEmpty(),
    phoneNumber = phoneNumber,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl,
    provider = provider ?: "anonymous"
)
