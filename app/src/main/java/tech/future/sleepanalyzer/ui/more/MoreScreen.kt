package tech.future.sleepanalyzer.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.future.sleepanalyzer.ui.stats.StatsViewModel
import tech.future.sleepanalyzer.ui.theme.SleepScore
import tech.future.sleepanalyzer.ui.theme.SleepSecondary

@Composable
fun MoreScreen(
    onNavigateToGoals: () -> Unit = {},
    onNavigateToSounds: () -> Unit = {},
    onNavigateToAlarm: () -> Unit = {},
    onNavigateToMoreOptions: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    statsViewModel: StatsViewModel = viewModel()
) {
    val sessions by statsViewModel.sessions.collectAsStateWithLifecycle()
    val avgScore by statsViewModel.averageScore.collectAsStateWithLifecycle()
    val avgDuration by statsViewModel.averageDuration.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        // 2x2 stat grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCell(
                icon = Icons.Default.Nightlight,
                value = "${sessions.size}",
                label = "Nights",
                tint = SleepSecondary,
                modifier = Modifier.weight(1f)
            )
            StatCell(
                icon = Icons.Default.Flag,
                value = avgScore?.let { "${it.toInt()}%" } ?: "—",
                label = "Avg. quality",
                tint = SleepScore,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCell(
                icon = Icons.Default.Schedule,
                value = avgDuration?.let { "${it.toInt() / 60}h ${it.toInt() % 60}m" } ?: "—",
                label = "Avg. time",
                tint = SleepSecondary,
                modifier = Modifier.weight(1f)
            )
            StatCell(
                icon = Icons.Default.CloudDone,
                value = "OK",
                label = "Backup",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        SectionHeader("Settings")
        SettingsRow(Icons.Default.Flag, "Sleep Goal", "Not set", onNavigateToGoals)
        SettingsRow(Icons.Default.MusicNote, "Sound", "Ambient", onNavigateToSounds)
        SettingsRow(Icons.Default.Bedtime, "Wake up phase", "30 min", onNavigateToAlarm)
        SettingsRow(Icons.Default.Summarize, "Weekly report", "On", onNavigateToSettings)
        SettingsRow(Icons.Default.MoreHoriz, "More", null, onNavigateToMoreOptions)

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Premium")
        SettingsRow(Icons.Default.CloudDone, "Online backup", "On", onNavigateToSettings)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatCell(
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = SleepSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
