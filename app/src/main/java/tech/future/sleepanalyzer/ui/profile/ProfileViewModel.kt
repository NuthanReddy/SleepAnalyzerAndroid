package tech.future.sleepanalyzer.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.data.db.entity.UserProfile
import tech.future.sleepanalyzer.di.ServiceLocator

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository
        get() = ServiceLocator.repository

    private val _profile = MutableStateFlow(UserProfile.empty())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _autofillResult = MutableStateFlow<AutofillState>(AutofillState.Idle)
    val autofillResult: StateFlow<AutofillState> = _autofillResult.asStateFlow()

    private val _healthConnectAvailable = MutableStateFlow(false)
    val healthConnectAvailable: StateFlow<Boolean> = _healthConnectAvailable.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeUserProfile().collect { stored ->
                _profile.value = stored ?: UserProfile.empty()
            }
        }
        viewModelScope.launch {
            _healthConnectAvailable.value = hasHealthConnectAutofillSource(getApplication())
        }
    }

    fun setDisplayName(value: String) = updateProfile {
        copy(displayName = value.toNullableText())
    }

    fun setDateOfBirth(value: Long?) = updateProfile {
        copy(dateOfBirth = value)
    }

    fun setBiologicalSex(value: String?) = updateProfile {
        copy(biologicalSex = value.toNullableText())
    }

    fun setHeightCm(value: Float?) = updateProfile {
        copy(heightCm = value?.takeIf { it > 0f })
    }

    fun setWeightKg(value: Float?) = updateProfile {
        copy(weightKg = value?.takeIf { it > 0f })
    }

    fun setActivityLevel(value: String?) = updateProfile {
        copy(activityLevel = value.toNullableText())
    }

    fun setUnits(value: String) = updateProfile {
        copy(units = if (value == "imperial") "imperial" else "metric")
    }

    fun setSleepConditions(value: Set<String>) = updateProfile {
        copy(sleepConditions = normalizeSleepConditions(value))
    }

    fun setMedications(value: String) = updateProfile {
        copy(medications = value.toNullableText())
    }

    fun setTypicalCaffeineCutoffHour(value: Int?) = updateProfile {
        copy(typicalCaffeineCutoffHour = value?.coerceIn(0, 23))
    }

    fun setShiftWorkSchedule(value: String?) = updateProfile {
        copy(shiftWorkSchedule = value.toNullableText())
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
            updateProfile {
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

    suspend fun save() {
        val normalized = _profile.value.normalized()
        _profile.value = normalized
        repository.upsertUserProfile(normalized)
    }

    private fun updateProfile(transform: UserProfile.() -> UserProfile) {
        _profile.value = _profile.value.transform()
    }
}

private fun UserProfile.normalized(): UserProfile = copy(
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
