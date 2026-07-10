package tech.future.sleepanalyzer.ui.alarm

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.collectLatest
import tech.future.sleepanalyzer.data.db.entity.AlarmConfig
import tech.future.sleepanalyzer.util.PermissionsUtil

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val showEditor by viewModel.showEditor.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val notificationPermission = PermissionsUtil.notificationPermission()
    val notificationPermissionState = if (notificationPermission != null) {
        rememberPermissionState(notificationPermission)
    } else {
        null
    }
    var exactAlarmGranted by remember { mutableStateOf(PermissionsUtil.canScheduleExactAlarms(context)) }
    val notificationsGranted = notificationPermissionState?.status?.isGranted ?: true

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmGranted = PermissionsUtil.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.scheduleResult.collectLatest { scheduledExactly ->
            if (!scheduledExactly) {
                exactAlarmGranted = PermissionsUtil.canScheduleExactAlarms(context)
                val result = snackbarHostState.showSnackbar(
                    message = "Exact alarm permission needed for precise alarms",
                    actionLabel = "Open settings"
                )
                if (result == SnackbarResult.ActionPerformed) {
                    PermissionsUtil.exactAlarmSettingsIntent(context)?.let(context::startActivity)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onNewAlarm() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Alarm")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "Smart Alarm",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Wake up at the perfect moment",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (!notificationsGranted) {
                PermissionBanner(
                    title = "Enable notifications so the alarm can wake you",
                    actionLabel = "Grant",
                    onAction = { notificationPermissionState?.launchPermissionRequest() }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!exactAlarmGranted) {
                PermissionBanner(
                    title = "Allow exact alarms for precise wake-ups",
                    actionLabel = "Allow exact alarms",
                    onAction = { PermissionsUtil.exactAlarmSettingsIntent(context)?.let(context::startActivity) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (alarms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No alarms set",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap + to create your first alarm",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            onToggle = { viewModel.toggleAlarm(alarm) },
                            onEdit = { viewModel.onEditAlarm(alarm) },
                            onDelete = { viewModel.deleteAlarm(alarm) }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        AlarmEditorDialog(viewModel = viewModel)
    }
}

@Composable
private fun PermissionBanner(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun AlarmCard(
    alarm: AlarmConfig,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val timeText = String.format("%02d:%02d", alarm.hour, alarm.minute)
    val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
    val enabledDays = alarm.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    val showsSmartWake = alarm.useSmartWake && alarm.wakeWindowMinutes > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        timeText,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (alarm.label.isNotBlank()) {
                        Text(
                            alarm.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (showsSmartWake) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "smart",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = { onToggle() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dayNames.forEachIndexed { index, name ->
                    val dayNum = index + 1
                    val isEnabled = enabledDays.contains(dayNum)
                    val bgColor by animateColorAsState(
                        targetValue = if (isEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface,
                        label = ""
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name,
                            fontSize = 12.sp,
                            color = if (isEnabled) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (showsSmartWake) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Wake window: ${alarm.wakeWindowMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AlarmEditorDialog(viewModel: AlarmViewModel) {
    val hour by viewModel.editHour.collectAsStateWithLifecycle()
    val minute by viewModel.editMinute.collectAsStateWithLifecycle()
    val wakeWindow by viewModel.editWakeWindow.collectAsStateWithLifecycle()
    val useSmartWake by viewModel.editUseSmartWake.collectAsStateWithLifecycle()
    val days by viewModel.editDays.collectAsStateWithLifecycle()
    val label by viewModel.editLabel.collectAsStateWithLifecycle()
    val vibration by viewModel.editVibration.collectAsStateWithLifecycle()
    val snooze by viewModel.editSnooze.collectAsStateWithLifecycle()
    val selectedAlarm by viewModel.selectedAlarm.collectAsStateWithLifecycle()

    val timePickerState = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = true
    )

    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
        viewModel.onHourChanged(timePickerState.hour)
        viewModel.onMinuteChanged(timePickerState.minute)
    }

    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    AlertDialog(
        onDismissRequest = { viewModel.onDismissEditor() },
        title = { Text(if (selectedAlarm == null) "New Alarm" else "Edit Alarm") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    TimePicker(state = timePickerState)
                }
                item {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { viewModel.onLabelChanged(it) },
                        label = { Text("Label") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("Repeat", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        dayNames.forEachIndexed { index, name ->
                            val dayNum = index + 1
                            val selected = days.contains(dayNum)
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.onDayToggled(dayNum) },
                                label = { Text(name.take(1), fontSize = 11.sp) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Smart wake", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Wake within the window at light sleep",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useSmartWake,
                            onCheckedChange = { viewModel.onUseSmartWakeChanged(it) }
                        )
                    }
                }
                item {
                    Text(
                        if (useSmartWake) "Wake-up window: $wakeWindow min" else "Wake-up window: Fires exactly at the set time",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (useSmartWake) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = wakeWindow.toFloat(),
                        onValueChange = { viewModel.onWakeWindowChanged(it.toInt()) },
                        valueRange = 0f..90f,
                        steps = 5,
                        enabled = useSmartWake
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Vibration")
                        Switch(checked = vibration, onCheckedChange = { viewModel.onVibrationChanged(it) })
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Snooze")
                        Switch(checked = snooze, onCheckedChange = { viewModel.onSnoozeChanged(it) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.saveAlarm() }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.onDismissEditor() }) {
                Text("Cancel")
            }
        }
    )
}
