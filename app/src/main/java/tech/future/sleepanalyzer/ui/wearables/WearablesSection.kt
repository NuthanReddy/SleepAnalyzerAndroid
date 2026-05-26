package tech.future.sleepanalyzer.ui.wearables

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.data.db.entity.WearableDevice

@Composable
fun WearablesSection(
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    onPermissionResult: (Boolean) -> Unit = {},
    viewModel: WearablesViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val availableSources by viewModel.availableSources.collectAsStateWithLifecycle()
    val connectedDevices by viewModel.connectedDevices.collectAsStateWithLifecycle()
    val permissionsGranted by viewModel.permissionsGranted.collectAsStateWithLifecycle()
    val lastSyncCount by viewModel.lastSyncCount.collectAsStateWithLifecycle()
    var requiredPermissions by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(availableSources.size) {
        viewModel.recheckPermissions()
        requiredPermissions = viewModel.requiredPermissions()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { result ->
        val grantedPermissions = result as Set<String>
        val granted = grantedPermissions.containsAll(requiredPermissions)
        viewModel.recheckPermissions()
        if (granted) {
            viewModel.syncNow()
        }
        onPermissionResult(granted)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showHeader) {
            Text(
                text = "Wearables",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Optional — connect Health Connect to import heart rate and other sleep signals.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (availableSources.isEmpty()) {
            SourceStatusCard(
                sourceName = "Health Connect",
                stateText = "Not available"
            )
        } else {
            availableSources.forEach { source ->
                val sourceDevices = connectedDevices.filter { it.sourceProvider == source.id }
                SourceStatusCard(
                    sourceName = source.displayName,
                    stateText = sourceStatusText(
                        permissionsGranted = permissionsGranted,
                        devices = sourceDevices
                    )
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
                Text(
                    text = "Connected devices",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (connectedDevices.isEmpty()) {
                    Text(
                        text = "No wearable devices synced yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    connectedDevices.chunked(2).forEach { rowDevices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowDevices.forEach { device ->
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text("${device.displayName} • ${device.lastSyncMs.relativeSyncLabel()}")
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (availableSources.isEmpty()) {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Install Health Connect to connect wearables.",
                                        actionLabel = "Play Store"
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(PLAY_STORE_URL)
                                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                } else {
                                    // Always re-fetch permissions inside the click handler so we never
                                    // launch with an empty set (silent no-op on Android). See backlog #20.
                                    val permissions = viewModel.requiredPermissions()
                                    requiredPermissions = permissions
                                    if (permissions.isEmpty()) {
                                        snackbarHostState.showSnackbar(
                                            message = "Health Connect didn't expose any permissions to request. Open the Health Connect app and try again."
                                        )
                                    } else {
                                        runCatching { permissionLauncher.launch(permissions) }
                                            .onFailure {
                                                snackbarHostState.showSnackbar(
                                                    "Could not open Health Connect. Open it from system settings."
                                                )
                                            }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Connect")
                    }
                    FilledTonalButton(
                        onClick = viewModel::syncNow,
                        enabled = permissionsGranted && availableSources.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sync now")
                    }
                }

                // Always-available escape hatch: open Health Connect directly so the user can
                // manage permissions or install the app, even if our launcher above fails silently.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(onClick = {
                        scope.launch {
                            val intents = listOf(
                                Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            var launched = false
                            for (intent in intents) {
                                val result = runCatching { context.startActivity(intent) }
                                if (result.isSuccess) { launched = true; break }
                            }
                            if (!launched) {
                                snackbarHostState.showSnackbar("Could not open Health Connect on this device.")
                            }
                        }
                    }) {
                        Text("Open Health Connect")
                    }
                }

                lastSyncCount?.let { count ->
                    Text(
                        text = "Last sync imported $count samples.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun SourceStatusCard(
    sourceName: String,
    stateText: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = sourceName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stateText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun sourceStatusText(
    permissionsGranted: Boolean,
    devices: List<WearableDevice>
): String {
    val latestSyncMs = devices.maxOfOrNull { it.lastSyncMs }
    return when {
        permissionsGranted && latestSyncMs != null && latestSyncMs > 0L -> {
            "Connected — last sync ${latestSyncMs.relativeSyncLabel()}"
        }
        permissionsGranted || devices.isNotEmpty() -> "Connected"
        else -> "Not connected"
    }
}

private fun Long.relativeSyncLabel(): String {
    if (this <= 0L) return "never"
    val minutes = ((System.currentTimeMillis() - this) / 60_000L).coerceAtLeast(0L)
    return when (minutes) {
        0L -> "just now"
        1L -> "1 min ago"
        else -> "$minutes min ago"
    }
}

private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
