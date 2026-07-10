package tech.future.sleepanalyzer.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsScreen(
    onBack: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAlarm: () -> Unit = {},
    onNavigateToGame: () -> Unit = {},
    onNavigateToRecorder: () -> Unit = {}
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("More") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionLabel("Personal")
            SettingsRow(Icons.Default.AccountCircle, "Account", null, onNavigateToSettings)
            SettingsRow(Icons.Default.Person, "About you", null, onNavigateToProfile)
            SettingsRow(Icons.Default.Lock, "Consent and privacy", null, onNavigateToPrivacy)
            SettingsRow(Icons.Default.FavoriteBorder, "Health Connect", "Not connected", onNavigateToSettings)

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("Alarm")
            SettingsRow(Icons.Default.Mic, "Motion detection", "Microphone", onNavigateToSettings)
            SettingsRow(Icons.Default.GraphicEq, "Sound detection", "20 nights", onNavigateToSettings)
            SettingsRow(Icons.Default.LocationOn, "Placement reminders", "On", onNavigateToSettings)
            SettingsRow(Icons.Default.Alarm, "Snooze", "Intelligent", onNavigateToAlarm)
            SettingsRow(Icons.Default.Notifications, "Vibration", "As backup", onNavigateToAlarm)
            SettingsRow(Icons.Default.Warning, "Battery warning", "On", onNavigateToSettings)

            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel("Other")
            SettingsRow(Icons.Default.List, "Database", "Export CSV", onNavigateToSettings)
            SettingsRow(Icons.Default.SportsEsports, "Alertness game", null, onNavigateToGame)
            SettingsRow(Icons.Default.Mic, "Sleep recorder", null, onNavigateToRecorder)
            SettingsRow(Icons.Default.Info, "Third-party software", null, onNavigateToPrivacy)

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Sleep Analyzer $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
    )
}
