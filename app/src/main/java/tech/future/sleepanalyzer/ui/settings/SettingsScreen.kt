package tech.future.sleepanalyzer.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import tech.future.sleepanalyzer.auth.AuthRepository
import tech.future.sleepanalyzer.data.prefs.AppPreferences
import tech.future.sleepanalyzer.di.ServiceLocator
import tech.future.sleepanalyzer.service.BedtimeDetectionService
import tech.future.sleepanalyzer.sync.CloudSyncScheduler
import tech.future.sleepanalyzer.sync.LocalBackupManager
import tech.future.sleepanalyzer.ui.wearables.WearablesSection
import tech.future.sleepanalyzer.util.PermissionsUtil

/**
 * The single "Settings" bottom-nav page. Everything the user can configure lives here, grouped by
 * how the options relate to each other, so there is no longer a separate catch-all settings screen
 * to duplicate toggles into.
 */
@Composable
fun SettingsScreen(
    onNavigateToGoals: () -> Unit = {},
    onNavigateToPrograms: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToGame: () -> Unit = {},
    onReEnroll: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    onSignedOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ServiceLocator.preferences }
    val repository = remember { ServiceLocator.repository }
    val authRepository = remember { ServiceLocator.authRepository }
    val dataRequestRepository = remember { ServiceLocator.dataRequestRepository }

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
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

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

    val backupManager = remember { LocalBackupManager(context, repository, prefs) }
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(LocalBackupManager.MIME_TYPE)
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
                    onSuccess = { summary ->
                        "Backup saved (${summary.databaseRecords} records, ${summary.mediaFiles} media files)."
                    },
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
                result.onSuccess { authRepository.reconcileAfterRestore() }
                val text = result.fold(
                    onSuccess = { summary ->
                        buildString {
                            append("Restored ${summary.databaseRecords} records and ${summary.mediaFiles} media files.")
                            if (summary.skippedLegacyRecordings > 0) {
                                append(" ${summary.skippedLegacyRecordings} legacy recording entries lacked audio and were skipped.")
                            }
                        }
                    },
                    onFailure = { it.message ?: "Restore failed." }
                )
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val launchPortableBackup = {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
            .format(java.util.Date())
        createBackupLauncher.launch(
            "sleep_analyzer_backup_$stamp.${LocalBackupManager.FILE_EXTENSION}"
        )
    }

    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete account?") },
            text = {
                Text(
                    if (signedIn?.provider == AuthRepository.LOCAL_PROVIDER) {
                        "This removes the local account identity. Your sleep data stays on this device unless you remove it separately."
                    } else {
                        "This submits deletion of synced account data, signs you out, and removes the account identity from this device."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        scope.launch {
                            val result = runCatching {
                                if (
                                    signedIn?.provider != AuthRepository.LOCAL_PROVIDER &&
                                    authRepository.isConfigured()
                                ) {
                                    val account = requireNotNull(userAccount) {
                                        "Account details are unavailable"
                                    }
                                    dataRequestRepository.submitDeleteRequest(account).getOrThrow()
                                }
                                authRepository.deleteAccount().getOrThrow()
                                prefs.setCloudSyncEnabled(false)
                            }
                            result.onSuccess {
                                onSignedOut()
                                Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Account deletion failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
        SettingsRow(Icons.Default.Person, "Profile", null, onNavigateToProfile)
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
        SettingsRow(Icons.Default.SelfImprovement, "Sleep programs", null, onNavigateToPrograms)
        SwitchRow(
            Icons.Default.Summarize,
            "Weekly report",
            "Get a summary of your sleep every week",
            weeklyReportEnabled,
            onCheckedChange = { scope.launch { prefs.setWeeklyReportEnabled(it) } }
        )

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Account")
        AccountSettingsSection(
            signedIn = signedIn,
            onSignIn = onNavigateToSignIn,
            onExport = {
                if (signedIn?.provider == AuthRepository.LOCAL_PROVIDER) {
                    launchPortableBackup()
                } else {
                    scope.launch {
                        val result = runCatching {
                            dataRequestRepository.submitExportRequest(
                                requireNotNull(userAccount) { "Account details are unavailable" }
                            ).getOrThrow()
                        }
                        val message = result.fold(
                            onSuccess = { "Account export requested" },
                            onFailure = { it.message ?: "Export request failed" }
                        )
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onSignOut = {
                scope.launch {
                    authRepository.signOut()
                    prefs.setCloudSyncEnabled(false)
                    onSignedOut()
                    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = { showDeleteAccountDialog = true }
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
        SwitchRow(
            Icons.Default.Mic,
            "Use microphone for sleep staging",
            "Helps when your phone is on the nightstand instead of the mattress",
            micForStagingEnabled,
            onCheckedChange = { scope.launch { prefs.setMicForStagingEnabled(it) } }
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
        SectionHeader("Games")
        SettingsRow(Icons.Default.SportsEsports, "Alertness game", null, onNavigateToGame)

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Backup & sync")
        SettingsRow(
            Icons.Default.Save,
            "Back up to file",
            "Includes settings, sleep data, recordings, and voice samples",
            onClick = launchPortableBackup
        )
        SettingsRow(
            Icons.Default.Restore,
            "Restore from file",
            null,
            onClick = {
                restoreBackupLauncher.launch(
                    arrayOf(
                        LocalBackupManager.MIME_TYPE,
                        "application/json",
                        "text/plain",
                        "*/*"
                    )
                )
            }
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
        SectionHeader("Privacy")
        SettingsRow(Icons.Default.Lock, "Consent and privacy", null, onNavigateToPrivacy)
        SettingsRow(Icons.Default.Info, "Third-party software", null, onNavigateToPrivacy)

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
