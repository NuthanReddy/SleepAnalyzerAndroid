package tech.future.sleepanalyzer.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.auth.AuthState
import tech.future.sleepanalyzer.di.ServiceLocator
import tech.future.sleepanalyzer.data.prefs.AppPreferences
import tech.future.sleepanalyzer.service.BedtimeDetectionService
import tech.future.sleepanalyzer.sync.CloudSyncScheduler
import tech.future.sleepanalyzer.wearables.HealthConnectSource
import tech.future.sleepanalyzer.ui.wearables.WearablesSection
import tech.future.sleepanalyzer.util.PermissionsUtil

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onReEnroll: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSignup: () -> Unit,
    onOpenPrivacy: () -> Unit = {},
    onSignedOut: (goHome: Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val preferences = remember { ServiceLocator.preferences }
    val repository = remember { ServiceLocator.repository }
    val authRepository = remember { ServiceLocator.authRepository }
    val voiceIsolationEnabled by preferences.voiceIsolationEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val micForStagingEnabled by preferences.micForStagingEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val eventMergeGapMs by preferences.eventMergeGapMsFlow.collectAsStateWithLifecycle(
        initialValue = AppPreferences.DEFAULT_EVENT_MERGE_GAP_MS
    )
    val bedtimeAutoDetectEnabled by preferences.bedtimeAutoDetectEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val detailedHealthContextEnabled by preferences.detailedHealthContextEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val detailedHealthPermissions = remember { HealthConnectSource(context).detailedContextPermissions() }
    val detailedHealthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { /* Reads are permission-gated; nothing to do with the result set here. */ }
    val cloudSyncEnabled by preferences.cloudSyncEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val activeVoiceProfile by repository.getActiveVoiceProfileFlow().collectAsStateWithLifecycle(initialValue = null)
    val userAccount by repository.observeUserAccount().collectAsStateWithLifecycle(initialValue = null)
    val authState by authRepository.state.collectAsStateWithLifecycle()
    val signedIn = authState as? AuthState.SignedIn
    val snackbarHostState = remember { SnackbarHostState() }
    var exactAlarmGranted by remember { mutableStateOf(PermissionsUtil.canScheduleExactAlarms(context)) }
    var notificationsGranted by remember { mutableStateOf(PermissionsUtil.canPostNotifications(context)) }

    val backupManager = remember { tech.future.sleepanalyzer.sync.LocalBackupManager(repository) }
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            backupManager.exportTo(out).getOrThrow()
                        } ?: throw java.io.IOException("Couldn't open file for writing")
                    }
                }
                result
                    .onSuccess { count -> snackbarHostState.showSnackbar("Backup saved ($count records).") }
                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "Backup failed.") }
            }
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            backupManager.importFrom(input).getOrThrow()
                        } ?: throw java.io.IOException("Couldn't open file for reading")
                    }
                }
                result
                    .onSuccess { count -> snackbarHostState.showSnackbar("Restored $count records.") }
                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "Restore failed.") }
            }
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmGranted = PermissionsUtil.canScheduleExactAlarms(context)
                notificationsGranted = PermissionsUtil.canPostNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Isolation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (activeVoiceProfile != null) {
                                "Use your enrolled voice profile to separate your sleep sounds."
                            } else {
                                "No voice profile saved yet. You can enroll one below."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = voiceIsolationEnabled,
                        onCheckedChange = {
                            scope.launch {
                                preferences.setVoiceIsolationEnabled(it)
                            }
                        }
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use microphone for sleep staging",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Helps when your phone is on the nightstand instead of the mattress",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = micForStagingEnabled,
                        onCheckedChange = {
                            scope.launch {
                                preferences.setMicForStagingEnabled(it)
                            }
                        }
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Merge nearby sound events",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Club snores/coughs that occur within " +
                            "${"%.1f".format(eventMergeGapMs / 1000f)}s into one recording instead of many tiny clips",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = eventMergeGapMs.toFloat(),
                        onValueChange = {
                            scope.launch { preferences.setEventMergeGapMs(it.toLong()) }
                        },
                        valueRange = AppPreferences.MIN_EVENT_MERGE_GAP_MS.toFloat()..
                            AppPreferences.MAX_EVENT_MERGE_GAP_MS.toFloat()
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Detect bedtime automatically",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Start tracking on its own once your screen is off and you've been still for a while. Uses motion only \u2014 no audio.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = bedtimeAutoDetectEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { preferences.setBedtimeAutoDetectEnabled(enabled) }
                            val intent = Intent(context, BedtimeDetectionService::class.java).apply {
                                action = if (enabled) {
                                    BedtimeDetectionService.ACTION_START
                                } else {
                                    BedtimeDetectionService.ACTION_STOP
                                }
                            }
                            if (enabled) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        }
                    )
                }
            }

            SettingsRow(
                icon = Icons.Default.SettingsVoice,
                title = "Re-enroll voice profile",
                subtitle = if (activeVoiceProfile != null) "Replace your current 5-second sample" else "Create your first voice profile",
                onClick = onReEnroll
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Detailed health context",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Read caffeine, hydration, and body-temperature logs from Health Connect to add context to your sleep reports. Opt-in; grants extra Health Connect permissions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = detailedHealthContextEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { preferences.setDetailedHealthContextEnabled(enabled) }
                            if (enabled) {
                                runCatching { detailedHealthPermissionLauncher.launch(detailedHealthPermissions) }
                            }
                        }
                    )
                }
            }

            SettingsRow(
                icon = Icons.Default.Schedule,
                title = "Exact alarm permission",
                subtitle = if (exactAlarmGranted) "Allowed" else "Allow exact alarms for reliable wake-ups",
                onClick = {
                    PermissionsUtil.exactAlarmSettingsIntent(context)?.let(context::startActivity)
                }
            )

            SettingsRow(
                icon = Icons.Default.Notifications,
                title = "Notification permission",
                subtitle = if (notificationsGranted) "Granted" else "Open app settings to enable notifications",
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

            SettingsRow(
                icon = Icons.Default.Person,
                title = "Open profile",
                subtitle = "Update your optional profile and personalization details",
                onClick = onOpenProfile
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Wearables",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WearablesSection(showHeader = false)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                                text = "Local backup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                    )
                    Text(
                                text = "Save all sleep data to a file you keep, then restore it after reinstalling.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsRow(
                                icon = Icons.Default.Save,
                                title = "Back up to file",
                                subtitle = "Export sessions, notes, goals and profile as JSON",
                                onClick = {
                                    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
                                        .format(java.util.Date())
                                    createBackupLauncher.launch("sleep_analyzer_backup_$stamp.json")
                                }
                    )
                    SettingsRow(
                                icon = Icons.Default.Restore,
                                title = "Restore from file",
                                subtitle = "Import a previously saved backup",
                                onClick = { restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cloud Sync",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sync profile, goals, summaries, sessions, audio-event metadata, and notes. Raw audio never leaves the device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = cloudSyncEnabled,
                            onCheckedChange = { enabled ->
                                if (signedIn == null) return@Switch
                                scope.launch {
                                    preferences.setCloudSyncEnabled(enabled)
                                    userAccount?.let { account ->
                                        repository.upsertUserAccount(account.copy(syncEnabled = enabled))
                                    }
                                }
                            },
                            enabled = signedIn != null
                        )
                    }
                    Text(
                        text = "Last sync: ${userAccount?.lastSyncMs.relativeSyncLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            CloudSyncScheduler.runOnce(context)
                            scope.launch {
                                snackbarHostState.showSnackbar("Cloud sync scheduled")
                            }
                        },
                        enabled = signedIn != null && cloudSyncEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync now")
                    }
                }
            }

            SettingsRow(
                icon = Icons.Default.Shield,
                title = "Privacy",
                subtitle = "What stays on-device, what syncs, and how export/delete work",
                onClick = onOpenPrivacy
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Voice samples stay on-device",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enrollment audio is saved privately under the app's voice_profiles folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
