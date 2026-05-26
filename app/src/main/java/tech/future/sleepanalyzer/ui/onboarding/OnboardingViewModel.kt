package tech.future.sleepanalyzer.ui.onboarding

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.future.sleepanalyzer.audio.AudioDispatchers
import tech.future.sleepanalyzer.audio.encoder.PcmToWavEncoder
import tech.future.sleepanalyzer.audio.isolation.VoiceProfileBuilder
import tech.future.sleepanalyzer.audio.source.AudioRecordSource
import tech.future.sleepanalyzer.auth.FIREBASE_NOT_CONFIGURED_MESSAGE
import tech.future.sleepanalyzer.auth.GoogleSignInCoordinator
import tech.future.sleepanalyzer.auth.PhoneAuthCoordinator
import tech.future.sleepanalyzer.auth.resolveDefaultWebClientId
import tech.future.sleepanalyzer.data.db.entity.UserProfile
import tech.future.sleepanalyzer.data.db.entity.VoiceProfile
import tech.future.sleepanalyzer.di.ServiceLocator
import tech.future.sleepanalyzer.ui.auth.SignupUiState
import tech.future.sleepanalyzer.ui.profile.AutofillState
import tech.future.sleepanalyzer.ui.profile.hasHealthConnectAutofillSource
import tech.future.sleepanalyzer.ui.profile.readHealthConnectAutofill
import tech.future.sleepanalyzer.util.Constants
import tech.future.sleepanalyzer.wearables.SourceRegistry
import tech.future.sleepanalyzer.wearables.WearableSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OnboardingStep {
    Welcome,
    Signup,
    Permissions,
    AboutYou,
    Wearables,
    VoiceIsolation,
    VoiceEnroll,
    Complete
}

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences
        get() = ServiceLocator.preferences

    private val repository
        get() = ServiceLocator.repository

    private val authRepository
        get() = ServiceLocator.authRepository

    private val phoneAuthCoordinator = PhoneAuthCoordinator()
    private val googleSignInCoordinator = GoogleSignInCoordinator()

    private val _currentStep = MutableStateFlow(OnboardingStep.Welcome)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    private val _profileDraft = MutableStateFlow(UserProfile.empty())
    val profileDraft: StateFlow<UserProfile> = _profileDraft.asStateFlow()

    private val _signupState = MutableStateFlow<SignupUiState>(SignupUiState.Idle)
    val signupState: StateFlow<SignupUiState> = _signupState.asStateFlow()

    private val _autofillResult = MutableStateFlow<AutofillState>(AutofillState.Idle)
    val autofillResult: StateFlow<AutofillState> = _autofillResult.asStateFlow()

    private val _healthConnectAutofillAvailable = MutableStateFlow(false)
    val healthConnectAutofillAvailable: StateFlow<Boolean> = _healthConnectAutofillAvailable.asStateFlow()

    private val _wearablePermissionsGranted = MutableStateFlow(false)
    val wearablePermissionsGranted: StateFlow<Boolean> = _wearablePermissionsGranted.asStateFlow()

    private val _enrollmentRecording = MutableStateFlow(false)
    val enrollmentRecording: StateFlow<Boolean> = _enrollmentRecording.asStateFlow()

    private val _enrollmentProgress = MutableStateFlow(0f)
    val enrollmentProgress: StateFlow<Float> = _enrollmentProgress.asStateFlow()

    private val _enrollmentResult = MutableStateFlow<VoiceProfile?>(null)
    val enrollmentResult: StateFlow<VoiceProfile?> = _enrollmentResult.asStateFlow()

    private val _enrollmentError = MutableStateFlow<String?>(null)
    val enrollmentError: StateFlow<String?> = _enrollmentError.asStateFlow()

    private var enrollmentJob: Job? = null
    @Volatile
    private var activeSource: AudioRecordSource? = null

    @Volatile
    private var pendingSampleFile: File? = null

    @Volatile
    private var cachedAvailableSources: List<WearableSource>? = null

    init {
        viewModelScope.launch {
            repository.getUserProfile()?.let { _profileDraft.value = it }
        }
        viewModelScope.launch {
            _healthConnectAutofillAvailable.value = hasHealthConnectAutofillSource(getApplication())
        }
    }

    fun nextStep(saveProfile: Boolean = true) {
        viewModelScope.launch {
            if (_currentStep.value == OnboardingStep.AboutYou && saveProfile) {
                repository.upsertUserProfile(_profileDraft.value.normalizedProfile())
            }
            _currentStep.value = when (_currentStep.value) {
                OnboardingStep.Welcome -> OnboardingStep.Signup
                OnboardingStep.Signup -> OnboardingStep.Permissions
                OnboardingStep.Permissions -> OnboardingStep.AboutYou
                OnboardingStep.AboutYou -> OnboardingStep.Wearables
                OnboardingStep.Wearables -> OnboardingStep.VoiceIsolation
                OnboardingStep.VoiceIsolation -> OnboardingStep.VoiceEnroll
                OnboardingStep.VoiceEnroll,
                OnboardingStep.Complete -> OnboardingStep.Complete
            }
        }
    }

    fun previousStep() {
        if (_currentStep.value == OnboardingStep.VoiceEnroll && _enrollmentRecording.value) {
            cancelEnrollment()
        }
        _currentStep.value = when (_currentStep.value) {
            OnboardingStep.Welcome -> OnboardingStep.Welcome
            OnboardingStep.Signup -> OnboardingStep.Welcome
            OnboardingStep.Permissions -> OnboardingStep.Signup
            OnboardingStep.AboutYou -> OnboardingStep.Permissions
            OnboardingStep.Wearables -> OnboardingStep.AboutYou
            OnboardingStep.VoiceIsolation -> OnboardingStep.Wearables
            OnboardingStep.VoiceEnroll -> OnboardingStep.VoiceIsolation
            OnboardingStep.Complete -> OnboardingStep.VoiceEnroll
        }
    }

    fun setDisplayName(value: String) = updateProfileDraft {
        copy(displayName = value.toNullableText())
    }

    fun setDateOfBirth(value: Long?) = updateProfileDraft {
        copy(dateOfBirth = value)
    }

    fun setBiologicalSex(value: String?) = updateProfileDraft {
        copy(biologicalSex = value.toNullableText())
    }

    fun setHeightCm(value: Float?) = updateProfileDraft {
        copy(heightCm = value?.takeIf { it > 0f })
    }

    fun setWeightKg(value: Float?) = updateProfileDraft {
        copy(weightKg = value?.takeIf { it > 0f })
    }

    fun setActivityLevel(value: String?) = updateProfileDraft {
        copy(activityLevel = value.toNullableText())
    }

    fun setUnits(value: String) = updateProfileDraft {
        copy(units = if (value == "imperial") "imperial" else "metric")
    }

    fun setSleepConditions(value: Set<String>) = updateProfileDraft {
        copy(sleepConditions = normalizeSleepConditions(value))
    }

    fun setMedications(value: String) = updateProfileDraft {
        copy(medications = value.toNullableText())
    }

    fun setTypicalCaffeineCutoffHour(value: Int?) = updateProfileDraft {
        copy(typicalCaffeineCutoffHour = value?.coerceIn(0, 23))
    }

    fun setShiftWorkSchedule(value: String?) = updateProfileDraft {
        copy(shiftWorkSchedule = value.toNullableText())
    }

    fun startPhoneVerification(activity: Activity, phoneE164: String) {
        if (!authRepository.isConfigured()) {
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
        if (!authRepository.isConfigured()) {
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
        if (!authRepository.isConfigured()) {
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
            }.onSuccess { account ->
                updateProfileDraft {
                    copy(
                        displayName = account.displayName ?: displayName,
                        updatedAt = System.currentTimeMillis()
                    )
                }
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

    fun autofillFromHealthConnect() {
        viewModelScope.launch {
            _autofillResult.value = AutofillState.Loading
            val data = readHealthConnectAutofill(getApplication())
            if (data == null) {
                _autofillResult.value = AutofillState.Failed("Health Connect is not available on this device.")
                return@launch
            }
            val filled = data.filledFields()
            if (filled.isEmpty()) {
                _autofillResult.value = AutofillState.Failed("No height or weight data found in Health Connect.")
                return@launch
            }
            updateProfileDraft {
                copy(
                    heightCm = data.heightCm ?: heightCm,
                    weightKg = data.weightKg ?: weightKg
                )
            }
            _autofillResult.value = AutofillState.Success(filled)
        }
    }

    fun clearAutofillResult() {
        _autofillResult.value = AutofillState.Idle
    }

    fun markWearablePermissionsGranted(value: Boolean) {
        _wearablePermissionsGranted.value = value
    }

    suspend fun availableSources(): List<WearableSource> {
        return cachedAvailableSources ?: SourceRegistry.available(getApplication()).also {
            cachedAvailableSources = it
        }
    }

    private fun updateProfileDraft(transform: UserProfile.() -> UserProfile) {
        _profileDraft.value = _profileDraft.value.transform()
    }

    fun enableVoiceIsolation(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setVoiceIsolationEnabled(enabled)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            completeOnboardingInternal()
        }
    }

    fun finishOnboardingWithVoiceIsolation() {
        viewModelScope.launch {
            preferences.setVoiceIsolationEnabled(true)
            completeOnboardingInternal()
        }
    }

    fun skipVoiceIsolation() {
        viewModelScope.launch {
            preferences.setVoiceIsolationEnabled(false)
            completeOnboardingInternal()
        }
    }

    fun startEnrollment() {
        if (enrollmentJob?.isActive == true) return

        // Run on Dispatchers.Default — NOT AudioDispatchers.capture. The capture dispatcher is
        // single-threaded and is where AudioRecordSource's producer also runs (via flowOn(capture)).
        // Putting the collector on the same thread would deadlock: the producer blocks in
        // AudioRecord.read() and never yields, so the consumer (this lambda) never gets to update
        // progress or persist the result. See docs/backlog.md #19 for full diagnosis.
        enrollmentJob = viewModelScope.launch(Dispatchers.Default) {
            _enrollmentRecording.value = true
            _enrollmentProgress.value = 0f
            _enrollmentResult.value = null
            _enrollmentError.value = null
            pendingSampleFile?.takeIf(File::exists)?.delete()
            pendingSampleFile = null

            val requiredSamples = Constants.AUDIO_SAMPLE_RATE * ENROLLMENT_SECONDS
            val pcmBuffer = ShortArray(requiredSamples)
            val builder = VoiceProfileBuilder(Constants.AUDIO_SAMPLE_RATE)
            val source = AudioRecordSource(Constants.AUDIO_SAMPLE_RATE)
            activeSource = source
            var totalSamples = 0
            var completed = false

            try {
                source.frames()
                    .takeWhile { frame ->
                        val remaining = requiredSamples - totalSamples
                        val copyCount = minOf(frame.length, remaining)
                        if (copyCount > 0) {
                            System.arraycopy(frame.samples, 0, pcmBuffer, totalSamples, copyCount)
                            builder.feed(frame.samples.copyOf(copyCount))
                            totalSamples += copyCount
                            _enrollmentProgress.value =
                                (totalSamples.toFloat() / requiredSamples).coerceIn(0f, 1f)
                        }
                        totalSamples < requiredSamples
                    }
                    .collect { }

                if (totalSamples == 0) {
                    _enrollmentError.value = "Could not capture audio. Please try again."
                    return@launch
                }

                // Stop the source as soon as we have enough samples — don't keep AudioRecord
                // running while we encode + persist. This also lets the UI flip out of
                // "recording" state during the save phase below.
                runCatching { source.stop() }

                val profile = builder.build()
                // Wrap save + persist in a timeout. If file encode or DB write hangs (was
                // observed with MediaCodec on some devices) we surface an error instead of
                // leaving the UI stuck on "Cancel enrollment".
                val savedProfile = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                    val filePath = withContext(AudioDispatchers.io) {
                        saveEnrollmentSample(pcmBuffer.copyOf(totalSamples), Constants.AUDIO_SAMPLE_RATE)
                    } ?: return@withTimeoutOrNull null
                    val stamped = profile.copy(sampleFilePath = filePath)
                    val savedId = withContext(AudioDispatchers.io) {
                        repository.replaceVoiceProfile(stamped)
                    }
                    stamped.copy(id = savedId, isActive = true)
                }

                if (savedProfile == null) {
                    _enrollmentError.value = "Could not save the voice sample. Please try again."
                    return@launch
                }

                _enrollmentResult.value = savedProfile
                _enrollmentProgress.value = 1f
                pendingSampleFile = null
                completed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _enrollmentError.value = "Enrollment failed. Please try again."
            } finally {
                source.stop()
                activeSource = null
                builder.reset()
                if (!completed) {
                    pendingSampleFile?.takeIf(File::exists)?.delete()
                    pendingSampleFile = null
                    _enrollmentResult.value = null
                    _enrollmentProgress.value = 0f
                }
                _enrollmentRecording.value = false
            }
        }
    }

    fun cancelEnrollment() {
        activeSource?.stop()
        enrollmentJob?.cancel()
        enrollmentJob = null
        pendingSampleFile?.takeIf(File::exists)?.delete()
        pendingSampleFile = null
        _enrollmentRecording.value = false
        _enrollmentProgress.value = 0f
        _enrollmentResult.value = null
        _enrollmentError.value = null
    }

    override fun onCleared() {
        cancelEnrollment()
        super.onCleared()
    }

    private suspend fun completeOnboardingInternal() {
        preferences.setSetupCompleted(true)
        _currentStep.value = OnboardingStep.Complete
    }

    private fun saveEnrollmentSample(pcm: ShortArray, sampleRate: Int): String? {
        val app = getApplication<Application>()
        val directory = File(app.filesDir, "voice_profiles").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        // Use WAV directly for enrollment. The 5-second sample is small (~480 KB at 48 kHz)
        // and WAV avoids MediaCodec deadlocks observed on some devices that would leave the
        // enrollment UI stuck on "Cancel enrollment" forever.
        val encoder = PcmToWavEncoder()
        val file = File(directory, "voice_profile_$timestamp.${encoder.outputExtension}")
        pendingSampleFile = file
        return if (encoder.encode(pcm, sampleRate, file)) {
            file.absolutePath
        } else {
            file.delete()
            null
        }
    }

    companion object {
        private const val ENROLLMENT_SECONDS = 5
    }
}

private fun UserProfile.normalizedProfile(): UserProfile = copy(
    displayName = displayName.toNullableText(),
    biologicalSex = biologicalSex.toNullableText(),
    heightCm = heightCm?.takeIf { it > 0f },
    weightKg = weightKg?.takeIf { it > 0f },
    activityLevel = activityLevel.toNullableText(),
    units = if (units == "imperial") "imperial" else "metric",
    sleepConditions = normalizeSleepConditions(conditionsList.toSet()),
    medications = medications.toNullableText(),
    typicalCaffeineCutoffHour = typicalCaffeineCutoffHour?.coerceIn(0, 23),
    shiftWorkSchedule = shiftWorkSchedule.toNullableText()
)

private fun String?.toNullableText(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun normalizeSleepConditions(raw: Set<String>): String {
    val normalized = raw
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .toSet()
        .let { conditions ->
            when {
                "none" in conditions && conditions.size > 1 -> conditions - "none"
                else -> conditions
            }
        }
    return normalized.sorted().joinToString(",")
}
