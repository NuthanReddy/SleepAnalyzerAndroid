package tech.future.sleepanalyzer.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.future.sleepanalyzer.ui.theme.SleepScore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepGoalScreen(
    viewModel: GoalsViewModel = viewModel()
) {
    val goal by viewModel.activeGoal.collectAsStateWithLifecycle()
    val showEditor by viewModel.showEditor.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Sleep Goals",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Set targets for better sleep",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        goal?.let { g ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, contentDescription = "Goal", tint = SleepScore)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Your Sleep Goal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { viewModel.showGoalEditor() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    GoalRow("🛏️ Bedtime", String.format("%02d:%02d", g.targetBedtimeHour, g.targetBedtimeMinute))
                    GoalRow("⏰ Wake up", String.format("%02d:%02d", g.targetWakeHour, g.targetWakeMinute))
                    GoalRow("⏱️ Duration", "${g.targetDurationMinutes / 60}h ${g.targetDurationMinutes % 60}m")
                    GoalRow("⭐ Target Score", "${g.targetScore}/100")
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎯", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No sleep goal set yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.showGoalEditor() }) {
                        Text("Set Your Goal")
                    }
                }
            }
        }
    }

    if (showEditor) {
        GoalEditorDialog(viewModel)
    }
}

@Composable
fun GoalRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorDialog(viewModel: GoalsViewModel) {
    val bedH by viewModel.editBedtimeHour.collectAsStateWithLifecycle()
    val bedM by viewModel.editBedtimeMinute.collectAsStateWithLifecycle()
    val wakeH by viewModel.editWakeHour.collectAsStateWithLifecycle()
    val wakeM by viewModel.editWakeMinute.collectAsStateWithLifecycle()
    val targetScore by viewModel.editTargetScore.collectAsStateWithLifecycle()

    val bedtimePicker = rememberTimePickerState(initialHour = bedH, initialMinute = bedM, is24Hour = true)
    val wakePicker = rememberTimePickerState(initialHour = wakeH, initialMinute = wakeM, is24Hour = true)

    LaunchedEffect(bedtimePicker.hour, bedtimePicker.minute) {
        viewModel.onBedtimeHourChanged(bedtimePicker.hour)
        viewModel.onBedtimeMinuteChanged(bedtimePicker.minute)
    }
    LaunchedEffect(wakePicker.hour, wakePicker.minute) {
        viewModel.onWakeHourChanged(wakePicker.hour)
        viewModel.onWakeMinuteChanged(wakePicker.minute)
    }

    AlertDialog(
        onDismissRequest = { viewModel.dismissEditor() },
        title = { Text("Set Sleep Goal") },
        text = {
            Column {
                Text("Bedtime", style = MaterialTheme.typography.titleSmall)
                TimePicker(state = bedtimePicker)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Wake Time", style = MaterialTheme.typography.titleSmall)
                TimePicker(state = wakePicker)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Target Quality Score: $targetScore",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = targetScore.toFloat(),
                    onValueChange = { viewModel.onTargetScoreChanged(it.toInt()) },
                    valueRange = 50f..100f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.saveGoal() }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissEditor() }) {
                Text("Cancel")
            }
        }
    )
}
