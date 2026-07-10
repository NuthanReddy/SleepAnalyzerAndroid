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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.data.prefs.AppPreferences
import tech.future.sleepanalyzer.ui.theme.SleepSecondary

@Composable
fun MoreScreen(
    onNavigateToGoals: () -> Unit = {},
    onNavigateToSounds: () -> Unit = {},
    onNavigateToAlarm: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToGame: () -> Unit = {},
    onNavigateToRecorder: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember(context) { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val weeklyReportEnabled by prefs.weeklyReportEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val voiceIsolationEnabled by prefs.voiceIsolationEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val micForStagingEnabled by prefs.micForStagingEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val bedtimeAutoDetectEnabled by prefs.bedtimeAutoDetectEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val detailedHealthContextEnabled by prefs.detailedHealthContextEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(28.dp))
        SectionHeader("General")
        SettingsRow(Icons.Default.Flag, "Sleep Goal", "Not set", onNavigateToGoals)
        SettingsRow(Icons.Default.MusicNote, "Sound", "Ambient", onNavigateToSounds)
        SettingsRow(Icons.Default.Bedtime, "Smart Alarms", "30 min", onNavigateToAlarm)
        SwitchRow(
            Icons.Default.Summarize,
            "Weekly report",
            null,
            weeklyReportEnabled,
            onCheckedChange = { scope.launch { prefs.setWeeklyReportEnabled(it) } }
        )

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Recording & Detection")
        SwitchRow(
            Icons.Default.GraphicEq,
            "Voice isolation",
            "Use your enrolled voice profile to separate your sleep sounds",
            voiceIsolationEnabled,
            onCheckedChange = { scope.launch { prefs.setVoiceIsolationEnabled(it) } }
        )
        SwitchRow(
            Icons.Default.Mic,
            "Microphone for sleep staging",
            "Helps when your phone is on the nightstand instead of the mattress",
            micForStagingEnabled,
            onCheckedChange = { scope.launch { prefs.setMicForStagingEnabled(it) } }
        )
        SwitchRow(
            Icons.Default.Bedtime,
            "Detect bedtime automatically",
            "Start tracking once your screen is off and you've been still. Motion only \u2014 no audio",
            bedtimeAutoDetectEnabled,
            onCheckedChange = { scope.launch { prefs.setBedtimeAutoDetectEnabled(it) } }
        )
        SwitchRow(
            Icons.Default.FavoriteBorder,
            "Detailed health context",
            "Read caffeine, hydration, and body-temperature logs from Health Connect for richer reports",
            detailedHealthContextEnabled,
            onCheckedChange = { scope.launch { prefs.setDetailedHealthContextEnabled(it) } }
        )

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Personal")
        SettingsRow(Icons.Default.AccountCircle, "Account", null, onNavigateToSettings)
        SettingsRow(Icons.Default.Person, "About you", null, onNavigateToProfile)
        SettingsRow(Icons.Default.Lock, "Consent and privacy", null, onNavigateToPrivacy)
        SettingsRow(Icons.Default.FavoriteBorder, "Health Connect", "Not connected", onNavigateToSettings)

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Alarm")
        SettingsRow(Icons.Default.Mic, "Motion detection", "Microphone", onNavigateToSettings)
        SettingsRow(Icons.Default.GraphicEq, "Sound detection", "20 nights", onNavigateToSettings)
        SettingsRow(Icons.Default.LocationOn, "Placement reminders", "On", onNavigateToSettings)
        SettingsRow(Icons.Default.Alarm, "Snooze", "Intelligent", onNavigateToAlarm)
        SettingsRow(Icons.Default.Notifications, "Vibration", "As backup", onNavigateToAlarm)
        SettingsRow(Icons.Default.Warning, "Battery warning", "On", onNavigateToSettings)

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Other")
        SettingsRow(Icons.Default.List, "Database", "Export CSV", onNavigateToSettings)
        SettingsRow(Icons.Default.SportsEsports, "Alertness game", null, onNavigateToGame)
        SettingsRow(Icons.Default.Mic, "Sleep recorder", null, onNavigateToRecorder)
        SettingsRow(Icons.Default.Info, "Third-party software", null, onNavigateToPrivacy)

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Premium")
        SettingsRow(Icons.Default.CloudDone, "Online backup", "On", onNavigateToSettings)

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Sleep Analyzer $versionName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))
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
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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
