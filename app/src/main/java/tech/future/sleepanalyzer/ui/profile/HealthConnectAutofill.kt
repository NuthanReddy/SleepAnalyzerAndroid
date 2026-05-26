package tech.future.sleepanalyzer.ui.profile

import android.content.Context
import tech.future.sleepanalyzer.wearables.HealthConnectSource
import tech.future.sleepanalyzer.wearables.SourceRegistry

data class HealthConnectAutofillData(
    val heightCm: Float?,
    val weightKg: Float?
) {
    fun filledFields(): Set<String> = buildSet {
        if (heightCm != null) add("height")
        if (weightKg != null) add("weight")
    }
}

sealed interface AutofillState {
    data object Idle : AutofillState
    data object Loading : AutofillState
    data class Success(val filled: Set<String>) : AutofillState
    data class Failed(val message: String) : AutofillState
}

suspend fun hasHealthConnectAutofillSource(context: Context): Boolean =
    SourceRegistry.available(context).any { it is HealthConnectSource }

suspend fun readHealthConnectAutofill(context: Context): HealthConnectAutofillData? {
    val source = SourceRegistry.available(context).firstOrNull { it is HealthConnectSource } as? HealthConnectSource
        ?: return null
    return HealthConnectAutofillData(
        heightCm = source.readLatestHeightCm(),
        weightKg = source.readLatestWeightKg()
    )
}
