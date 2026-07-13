package tech.future.sleepanalyzer.ui.more

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.future.sleepanalyzer.auth.AuthState
import tech.future.sleepanalyzer.data.prefs.AppPreferences
import tech.future.sleepanalyzer.di.ServiceLocator
import tech.future.sleepanalyzer.service.BedtimeDetectionService
import tech.future.sleepanalyzer.sync.CloudSyncScheduler
import tech.future.sleepanalyzer.sync.LocalBackupManager
import tech.future.sleepanalyzer.ui.theme.SleepSecondary
import tech.future.sleepanalyzer.ui.wearables.WearablesSection
import tech.future.sleepanalyzer.util.PermissionsUtil

/**
 * The single "Settings" bottom-nav page. Everything the user can configure lives here, grouped by
 * how the options relate to each other, so there is no longer a separate catch-all settings screen
 * to duplicate toggles into.
 */
@Composable
fun MoreScreen(
    onNavigateToGoals: () -> Unit = {},
    onNavigateToSounds: () -> Unit = {},
    onNavigateToAlarm: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToGame: () -> Unit = {},
    onReEnroll: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ServiceLocator.preferences }
    val repository = remember { ServiceLocator.repository }
    val authRepository = remember { ServiceLocator.authRepository }

    val weeklyReportEnabled by prefs.weeklyReportEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val voiceIsolationEnabled by prefs.voiceIsolationEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val micForStagingEnabled by prefs.micForStagingEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val noiseReductionEnabled by prefs.noiseReductionEnabledFlow.collectAsStateWithLifecycle(initialValue = true)
    val bedtimeAutoDetectEnabled by prefs.bedtimeAutoDetectEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val detailedHealthContextEnabled by prefs.detailedHealthContextEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val cloudSyncEnabled by prefs.cloudSyncEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val eventMergeGapMs by prefs.eventMergeGapMsFlow.collectAsStateWithLifecycle(
        initialValue = AppPreferences.DEFAULT_EVENT_MERGE_GAP_MS
    )
    val goal by repository.getActiveGoal().collectAsStateWithLifecycle(initialValue = null)
    val activeVoiceProfile by repository.getActiveVoiceProfileFlow().collectAsStateWithLifecycle(initialValue = null)
    val userAccount by repository.observeUserAccount().collectAsStateWithLifecycle(initialValue = null)
    val authState by authRepository.state.collectAsStateWithLifecycle()
    val signedIn = authState as? AuthState.SignedIn

    val detailedHealthPermissions = remember { tech.future.sleepanalyzer.wearables.HealthConnectSource(context).detailedContextPermissions() }
    val detailedHealthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { /* Reads are permission-gated; nothing to do with the result set here. */ }

    var exactAlarmGranted by remember { mutableStateOf(PermissionsUtil.canScheduleExactAlarms(context)) }
    var notificationsGranted by remember { mutableStateOf(PermissionsUtil.canPostNotifications(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmGranted = PermissionsUtil.canScheduleExactAlarms(context)
                notificationsGranted = PermissionsUtil.canPostNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backupManager = remember { LocalBackupManager(repository) }
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            backupManager.exportTo(out).getOrThrow()
                        } ?: throw java.io.IOException("Couldn't open file for writing")
                    }
                }
                val text = result.fold(
                    onSuccess = { count -> "Backup saved ($count records)." },
                    onFailure = { it.message ?: "Backup failed." }
                )
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            backupManager.importFrom(input).getOrThrow()
                        } ?: throw java.io.IOException("Couldn't open file for reading")
                    }
                }
                val text = result.fold(
                    onSuccess = { count -> "Restored $count records." },
                    onFailure = { it.message ?: "Restore failed." }
                )
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

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
        SettingsRow(
            Icons.Default.Flag,
            "Sleep goal",
            goal?.let { g ->
                val hours = g.targetDurationMinutes / 60
                val minutes = g.targetDurationMinutes % 60
                if (minutes == 0) "${hours}h" else "${hours}h ${minutes}m"
            } ?: "Not set",
            onNavigateToGoals
        )
        SettingsRow(Icons.Default.MusicNote, "Sleep-aid sounds", "Ambient", onNavigateToSounds)
        SettingsRow(Icons.Default.Bedtime, "Smart alarms", null, onNavigateToAlarm)
        SettingsRow(Icons.Default.Alarm, "Snooze", "Intelligent", onNavigateToAlarm)
        SwitchRow(
            Icons.Default.Summarize,
            "Weekly report",
            "Get a summary of your sleep every week",
            weeklyReportEnabled,
            onCheckedChange = { scope.launch { prefs.setWeeklyReportEnabled(it) } }
        )

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Sound detection")
        SwitchRow(
            Icons.Default.GraphicEq,
            "Voice isolation",
            if (activeVoiceProfile != null) {
                "Use your enrolled voice profile to separate your sleep sounds"
            } else {
                "No voice profile saved yet \u2014 re-enroll below to turn this on"
            },
            voiceIsolationEnabled,
            onCheckedChange = { scope.launch { prefs.setVoiceIsolationEnabled(it) } }
        )
        SettingsRow(
            Icons.Default.SettingsVoice,
            "Re-enroll voice profile",
            if (activeVoiceProfile != null) "Replace sample" else "Not set",
            onReEnroll
        )
        SwitchRow(
            Icons.Default.Mic,
            "Use microphone for sleep staging",
            "Helps when your phone is on the nightstand instead of the mattress",
            micForStagingEnabled,
            onCheckedChange = { scope.launch { prefs.setMicForStagingEnabled(it) } }
        )

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Recording & detection")
        SwitchRow(
            Icons.Default.Tune,
            "Background noise reduction",
            "Filter out steady noise like an AC, fan, or hum for cleaner snore, talk, and cough detection",
            noiseReductionEnabled,
            onCheckedChange = { scope.launch { prefs.setNoiseReductionEnabled(it) } }
        )
        SliderRow(
            Icons.Default.GraphicEq,
            "Merge nearby sound events",
            "Club snores/coughs within ${"%.1f".format(eventMergeGapMs / 1000f)}s into one recording instead of many tiny clips",
            value = eventMergeGapMs.toFloat(),
            valueRange = AppPreferences.MIN_EVENT_MERGE_GAP_MS.toFloat()..AppPreferences.MAX_EVENT_MERGE_GAP_MS.toFloat(),
            onValueChange = { scope.launch { prefs.setEventMergeGapMs(it.toLong()) } }
        )
        SwitchRow(
            Icons.Default.Bedtime,
            "Detect bedtime automatically",
            "Start tracking once your screen is off and you've been still. Motion only \u2014 no audio",
            bedtimeAutoDetectEnabled,
            onCheckedChange = { enabled ->
                scope.launch { prefs.setBedtimeAutoDetectEnabled(enabled) }
                val intent = Intent(context, BedtimeDetectionService::class.java).apply {
                    action = if (enabled) BedtimeDetectionService.ACTION_START else BedtimeDetectionService.ACTION_STOP
                }
                if (enabled) context.startForegroundService(intent) else context.startService(intent)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Health & wearables")
        WearablesSection(showHeader = false)
        SwitchRow(
            Icons.Default.FavoriteBorder,
            "Detailed health context",
            "Read caffeine, hydration, and body-temperature logs from Health Connect for richer reports",
            detailedHealthContextEnabled,
            onCheckedChange = { enabled ->
                scope.launch { prefs.setDetailedHealthContextEnabled(enabled) }
                if (enabled) runCatching { detailedHealthPermissionLauncher.launch(detailedHealthPermissions) }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Backup & sync")
        SettingsRow(
            Icons.Default.Save,
            "Back up to file",
            null,
            onClick = {
                val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
                createBackupLauncher.launch("sleep_analyzer_backup_$stamp.json")
            }
        )
        SettingsRow(
            Icons.Default.Restore,
            "Restore from file",
            null,
            onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        )
        SwitchRow(
            Icons.Default.CloudDone,
            "Cloud sync",
            if (signedIn != null) {
                "Last sync: ${userAccount?.lastSyncMs.relativeSyncLabel()}"
            } else {
                "Sign in to sync across devices"
            },
            cloudSyncEnabled,
            enabled = signedIn != null,
            onCheckedChange = { enabled ->
                scope.launch {
                    prefs.setCloudSyncEnabled(enabled)
                    userAccount?.let { account ->
                        repository.upsertUserAccount(account.copy(syncEnabled = enabled))
                    }
                }
            }
        )
        if (signedIn != null && cloudSyncEnabled) {
            SettingsRow(
                Icons.Default.Sync,
                "Sync now",
                null,
                onClick = {
                    CloudSyncScheduler.runOnce(context)
                    Toast.makeText(context, "Cloud sync scheduled", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Permissions")
        SettingsRow(
            Icons.Default.Schedule,
            "Exact alarms",
            if (exactAlarmGranted) "Allowed" else "Off",
            onClick = { PermissionsUtil.exactAlarmSettingsIntent(context)?.let(context::startActivity) }
        )
        SettingsRow(
            Icons.Default.Notifications,
            "Notifications",
            if (notificationsGranted) "Allowed" else "Off",
            onClick = {
                if (!notificationsGranted) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Personal")
        SettingsRow(Icons.Default.Person, "About you", null, onNavigateToProfile)
        SettingsRow(Icons.Default.Lock, "Consent and privacy", null, onNavigateToPrivacy)
        SettingsRow(Icons.Default.Info, "Third-party software", null, onNavigateToPrivacy)

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Other")
        SettingsRow(Icons.Default.SportsEsports, "Alertness game", null, onNavigateToGame)

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Sleep Analyzer $versionName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center
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
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
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
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = SleepSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
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

private fun Long?.relativeSyncLabel(): String {
    val value = this ?: return "never"
    if (value <= 0L) return "never"
    val minutes = ((System.currentTimeMillis() - value) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes == 0L -> "just now"
        minutes < 60L -> "$minutes min ago"
        minutes < 1_440L -> "${minutes / 60L} hr ago"
        else -> "${minutes / 1_440L} day ago"
    }
}
