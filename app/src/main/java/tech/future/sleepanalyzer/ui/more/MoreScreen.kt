package tech.future.sleepanalyzer.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MoreScreen(
    onNavigateToNotes: () -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    onNavigateToPrograms: () -> Unit = {},
    onNavigateToGame: () -> Unit = {},
    onNavigateToRecorder: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "More",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        MoreMenuItem(
            icon = Icons.AutoMirrored.Filled.Notes,
            title = "Sleep Notes",
            subtitle = "Track what affects your sleep",
            onClick = onNavigateToNotes
        )
        MoreMenuItem(
            icon = Icons.Default.Flag,
            title = "Sleep Goals",
            subtitle = "Set targets for better rest",
            onClick = onNavigateToGoals
        )
        MoreMenuItem(
            icon = Icons.Default.School,
            title = "Sleep Programs",
            subtitle = "Expert-guided sleep improvement",
            onClick = onNavigateToPrograms
        )
        MoreMenuItem(
            icon = Icons.Default.SportsEsports,
            title = "Alertness Game",
            subtitle = "Test your morning alertness",
            onClick = onNavigateToGame
        )
        MoreMenuItem(
            icon = Icons.Default.Mic,
            title = "Sleep Recorder",
            subtitle = "Record and analyze sleep sounds",
            onClick = onNavigateToRecorder
        )
        MoreMenuItem(
            icon = Icons.Default.BarChart,
            title = "Detailed Statistics",
            subtitle = "In-depth sleep analysis",
            onClick = onNavigateToStats
        )
        MoreMenuItem(
            icon = Icons.Default.Settings,
            title = "Settings",
            subtitle = "Permissions, alarms, and voice isolation",
            onClick = onNavigateToSettings
        )
    }
}

@Composable
fun MoreMenuItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
