package tech.future.sleepanalyzer.ui.profile

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.data.db.entity.UserProfile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Composable
fun ProfileEditorScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val profile = viewModel.profile.collectAsStateWithLifecycle().value
    val autofillResult = viewModel.autofillResult.collectAsStateWithLifecycle().value
    val healthConnectAvailable = viewModel.healthConnectAvailable.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(autofillResult) {
        when (val result = autofillResult) {
            is AutofillState.Success -> {
                val message = when (result.filled) {
                    setOf("height", "weight") -> "Filled height + weight from Health Connect"
                    setOf("height") -> "Filled height from Health Connect"
                    setOf("weight") -> "Filled weight from Health Connect"
                    else -> "Filled profile data from Health Connect"
                }
                snackbarHostState.showSnackbar(message)
                viewModel.clearAutofillResult()
            }
            is AutofillState.Failed -> {
                snackbarHostState.showSnackbar(result.message)
                viewModel.clearAutofillResult()
            }
            else -> Unit
        }
    }

    val saveAndReturn: () -> Unit = {
        scope.launch {
            viewModel.save()
            onBack()
        }
    }

    BackHandler(onBack = saveAndReturn)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = saveAndReturn) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Optional — used to personalize sleep insights",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ProfileForm(
                profile = profile,
                onDisplayNameChange = viewModel::setDisplayName,
                onDateOfBirthChange = viewModel::setDateOfBirth,
                onBiologicalSexChange = viewModel::setBiologicalSex,
                onHeightCmChange = viewModel::setHeightCm,
                onWeightKgChange = viewModel::setWeightKg,
                onActivityLevelChange = viewModel::setActivityLevel,
                onUnitsChange = viewModel::setUnits,
                onSleepConditionsChange = viewModel::setSleepConditions,
                onMedicationsChange = viewModel::setMedications,
                onTypicalCaffeineCutoffHourChange = viewModel::setTypicalCaffeineCutoffHour,
                onShiftWorkScheduleChange = viewModel::setShiftWorkSchedule,
                bodyHeader = {
                    OutlinedButton(
                        onClick = viewModel::autofillFromHealthConnect,
                        enabled = healthConnectAvailable,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Autofill from Health Connect")
                    }
                }
            )

            Button(
                onClick = {
                    scope.launch {
                        viewModel.save()
                        snackbarHostState.showSnackbar("Profile saved")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save profile")
            }
        }
    }
}

@Composable
fun ProfileForm(
    profile: UserProfile,
    onDisplayNameChange: (String) -> Unit,
    onDateOfBirthChange: (Long?) -> Unit,
    onBiologicalSexChange: (String?) -> Unit,
    onHeightCmChange: (Float?) -> Unit,
    onWeightKgChange: (Float?) -> Unit,
    onActivityLevelChange: (String?) -> Unit,
    onUnitsChange: (String) -> Unit,
    onSleepConditionsChange: (Set<String>) -> Unit,
    onMedicationsChange: (String) -> Unit,
    onTypicalCaffeineCutoffHourChange: (Int?) -> Unit,
    onShiftWorkScheduleChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    bodyHeader: (@Composable ColumnScope.() -> Unit)? = null
) {
    val sectionSpacing = if (compact) 12.dp else 16.dp
    val units = profile.units
    val selectedConditions = profile.conditionsList.toSet()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
    ) {
        ProfileSectionCard(title = "Identity", compact = compact) {
            OutlinedTextField(
                value = profile.displayName.orEmpty(),
                onValueChange = onDisplayNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Display name") },
                singleLine = true
            )
            DateOfBirthField(
                value = profile.dateOfBirth,
                onValueChange = onDateOfBirthChange,
                compact = compact
            )
            SingleChoiceChipRows(
                label = "Biological sex",
                selectedKey = profile.biologicalSex,
                options = PROFILE_SEX_OPTIONS,
                compact = compact,
                onSelect = onBiologicalSexChange
            )
        }

        ProfileSectionCard(title = "Body", compact = compact) {
            bodyHeader?.invoke(this)
            RequiredChoiceChipRows(
                label = "Units",
                selectedKey = units,
                options = PROFILE_UNIT_OPTIONS,
                compact = compact,
                onSelect = onUnitsChange
            )
            OutlinedTextField(
                value = profile.displayHeightText(),
                onValueChange = { onHeightCmChange(it.parseHeightToCm(units)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (units == "imperial") "Height (in)" else "Height (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                singleLine = true
            )
            OutlinedTextField(
                value = profile.displayWeightText(),
                onValueChange = { onWeightKgChange(it.parseWeightToKg(units)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (units == "imperial") "Weight (lb)" else "Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                singleLine = true
            )
        }

        ProfileSectionCard(title = "Lifestyle", compact = compact) {
            SingleChoiceChipRows(
                label = "Activity level",
                selectedKey = profile.activityLevel,
                options = PROFILE_ACTIVITY_OPTIONS,
                compact = compact,
                onSelect = onActivityLevelChange
            )
            OutlinedTextField(
                value = profile.typicalCaffeineCutoffHour?.toString().orEmpty(),
                onValueChange = { onTypicalCaffeineCutoffHourChange(it.toIntOrNull()) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Typical caffeine cutoff hour (0-23)") },
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                singleLine = true
            )
            SingleChoiceChipRows(
                label = "Shift work schedule",
                selectedKey = profile.shiftWorkSchedule,
                options = PROFILE_SHIFT_OPTIONS,
                compact = compact,
                onSelect = onShiftWorkScheduleChange
            )
        }

        ProfileSectionCard(title = "Health", compact = compact) {
            MultiChoiceChipRows(
                label = "Sleep conditions",
                selectedKeys = selectedConditions,
                options = PROFILE_SLEEP_CONDITION_OPTIONS,
                compact = compact,
                onToggle = { key ->
                    onSleepConditionsChange(toggleSleepCondition(selectedConditions, key))
                }
            )
            OutlinedTextField(
                value = profile.medications.orEmpty(),
                onValueChange = onMedicationsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Medications") },
                minLines = if (compact) 2 else 3
            )
        }
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    compact: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Optional — used to personalize sleep insights",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun DateOfBirthField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    compact: Boolean
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Date of birth",
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    showDatePickerDialog(
                        context = context,
                        initialDateMs = value,
                        onDateSelected = onValueChange
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = value.formatProfileDate() ?: "Pick date of birth",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (value != null) {
                TextButton(onClick = { onValueChange(null) }) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun SingleChoiceChipRows(
    label: String,
    selectedKey: String?,
    options: List<ProfileOption>,
    compact: Boolean,
    onSelect: (String?) -> Unit
) {
    ChipRowsHeader(label = label, compact = compact)
    options.chunked(2).forEach { rowOptions ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowOptions.forEach { option ->
                FilterChip(
                    selected = selectedKey == option.key,
                    onClick = { onSelect(if (selectedKey == option.key) null else option.key) },
                    label = { Text(option.label) }
                )
            }
        }
    }
}

@Composable
private fun RequiredChoiceChipRows(
    label: String,
    selectedKey: String,
    options: List<ProfileOption>,
    compact: Boolean,
    onSelect: (String) -> Unit
) {
    ChipRowsHeader(label = label, compact = compact)
    options.chunked(2).forEach { rowOptions ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowOptions.forEach { option ->
                FilterChip(
                    selected = selectedKey == option.key,
                    onClick = { onSelect(option.key) },
                    label = { Text(option.label) }
                )
            }
        }
    }
}

@Composable
private fun MultiChoiceChipRows(
    label: String,
    selectedKeys: Set<String>,
    options: List<ProfileOption>,
    compact: Boolean,
    onToggle: (String) -> Unit
) {
    ChipRowsHeader(label = label, compact = compact)
    options.chunked(2).forEach { rowOptions ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowOptions.forEach { option ->
                FilterChip(
                    selected = option.key in selectedKeys,
                    onClick = { onToggle(option.key) },
                    label = { Text(option.label) }
                )
            }
        }
    }
}

@Composable
private fun ChipRowsHeader(label: String, compact: Boolean) {
    Text(
        text = label,
        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
}

private fun toggleSleepCondition(current: Set<String>, key: String): Set<String> {
    if (key == "none") {
        return if (key in current) emptySet() else setOf(key)
    }
    val updated = current.toMutableSet().apply {
        remove("none")
        if (contains(key)) remove(key) else add(key)
    }
    return updated
}

private fun UserProfile.displayHeightText(): String = when {
    heightCm == null -> ""
    units == "imperial" -> formatDecimal(heightCm / 2.54f)
    else -> formatDecimal(heightCm)
}

private fun UserProfile.displayWeightText(): String = when {
    weightKg == null -> ""
    units == "imperial" -> formatDecimal(weightKg * 2.20462f)
    else -> formatDecimal(weightKg)
}

private fun String.parseHeightToCm(units: String): Float? {
    val value = trim()
    if (value.isEmpty()) return null
    val parsed = value.toFloatOrNull() ?: return null
    return if (units == "imperial") parsed * 2.54f else parsed
}

private fun String.parseWeightToKg(units: String): Float? {
    val value = trim()
    if (value.isEmpty()) return null
    val parsed = value.toFloatOrNull() ?: return null
    return if (units == "imperial") parsed / 2.20462f else parsed
}

private fun formatDecimal(value: Float): String {
    return if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }
}

private fun Long?.formatProfileDate(): String? = this?.let {
    Instant.ofEpochMilli(it)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
}

private fun showDatePickerDialog(
    context: android.content.Context,
    initialDateMs: Long?,
    onDateSelected: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = initialDateMs ?: System.currentTimeMillis()
    }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selected = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onDateSelected(selected.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private data class ProfileOption(val key: String, val label: String)

private val PROFILE_SEX_OPTIONS = listOf(
    ProfileOption("male", "Male"),
    ProfileOption("female", "Female"),
    ProfileOption("other", "Other")
)

private val PROFILE_UNIT_OPTIONS = listOf(
    ProfileOption("metric", "Metric"),
    ProfileOption("imperial", "Imperial")
)

private val PROFILE_ACTIVITY_OPTIONS = listOf(
    ProfileOption("sedentary", "Sedentary"),
    ProfileOption("light", "Light"),
    ProfileOption("moderate", "Moderate"),
    ProfileOption("active", "Active"),
    ProfileOption("athlete", "Athlete")
)

private val PROFILE_SHIFT_OPTIONS = listOf(
    ProfileOption("none", "None"),
    ProfileOption("early", "Early"),
    ProfileOption("late", "Late"),
    ProfileOption("rotating", "Rotating"),
    ProfileOption("night", "Night")
)

private val PROFILE_SLEEP_CONDITION_OPTIONS = listOf(
    ProfileOption("sleep_apnea", "Sleep apnea"),
    ProfileOption("insomnia", "Insomnia"),
    ProfileOption("restless_legs", "Restless legs"),
    ProfileOption("narcolepsy", "Narcolepsy"),
    ProfileOption("none", "None")
)
