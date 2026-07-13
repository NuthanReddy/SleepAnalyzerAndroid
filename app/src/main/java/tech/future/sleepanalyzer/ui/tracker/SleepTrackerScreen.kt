package tech.future.sleepanalyzer.ui.tracker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.BatteryManager
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import tech.future.sleepanalyzer.data.db.entity.AlarmConfig
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.service.SoundPlayerService
import tech.future.sleepanalyzer.ui.theme.SleepAwake
import tech.future.sleepanalyzer.ui.theme.SleepOnBackground
import tech.future.sleepanalyzer.ui.theme.SleepOnSurfaceVariant
import tech.future.sleepanalyzer.ui.theme.SleepScore
import tech.future.sleepanalyzer.ui.theme.SleepSecondary
import tech.future.sleepanalyzer.ui.theme.SleepSurface
import tech.future.sleepanalyzer.ui.theme.SleepSurfaceVariant
import tech.future.sleepanalyzer.util.PermissionsUtil
import tech.future.sleepanalyzer.util.formatSleepDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SleepTrackerScreen(
    onNavigateToResult: (Long) -> Unit = {},
    onNavigateToSounds: () -> Unit = {},
    onNavigateToAlarm: () -> Unit = {},
    onNavigateToRecorder: () -> Unit = {},
    viewModel: SleepTrackerViewModel = viewModel()
) {
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val trackingStartTime by viewModel.trackingStartTime.collectAsStateWithLifecycle()
    val recentSessions by viewModel.recentSessions.collectAsStateWithLifecycle()
    val showMoodSelector by viewModel.showMoodSelector.collectAsStateWithLifecycle()
    val nextAlarm by viewModel.nextAlarm.collectAsStateWithLifecycle()
    val recordAudio by viewModel.recordAudioDuringTracking.collectAsStateWithLifecycle()
    val trackerPermissions = remember(recordAudio) {
        (PermissionsUtil.trackerPermissions() +
            if (recordAudio) listOf(Manifest.permission.RECORD_AUDIO) else emptyList()).distinct()
    }
    val context = LocalContext.current
    val recordAudioLatest = rememberUpdatedState(recordAudio)
    // True only while a Start-initiated permission request is in flight, so we can resume Start
    // from the permission *result* rather than a state key (notifications) that is already
    // satisfied — which would otherwise start tracking before the mic dialog is even answered.
    val awaitingPermissionForStart = remember { mutableStateOf(false) }
    // Low-battery guard: if the phone is nearly empty when the user starts a night, warn them to
    // plug in first. There's no persisted "Battery warning" preference yet, so the guard defaults
    // on. The check fails open (starts normally) if the battery level can't be read.
    val batteryWarningEnabled = true
    var showLowBatteryDialog by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    val startWithBatteryGuard: (() -> Unit) -> Unit = { start ->
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        if (batteryWarningEnabled && pct in 0..19) {
            pendingStart = start
            showLowBatteryDialog = true
        } else {
            start()
        }
    }
    val perms = rememberMultiplePermissionsState(trackerPermissions) { result ->
        if (awaitingPermissionForStart.value) {
            awaitingPermissionForStart.value = false
            val essentialOk = result
                .filterKeys { it != Manifest.permission.RECORD_AUDIO }
                .all { it.value }
            if (essentialOk) {
                val micOk = result[Manifest.permission.RECORD_AUDIO] == true
                startWithBatteryGuard {
                    viewModel.startTracking()
                    val msg = if (recordAudioLatest.value && !micOk) {
                        "Tracking started — mic denied, audio won't be recorded"
                    } else {
                        "Tracking started"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // Tracking itself only needs the non-audio permissions (e.g. notifications). The mic is
    // requested when "Record audio" is on so clips actually get captured, but a denied mic must
    // not block starting a night — in that case we just track without audio.
    val essentialPermissionsGranted = perms.permissions
        .filter { it.permission != Manifest.permission.RECORD_AUDIO }
        .all { it.status.isGranted }
    val micGranted = perms.permissions
        .firstOrNull { it.permission == Manifest.permission.RECORD_AUDIO }
        ?.status?.isGranted == true

    LaunchedEffect(Unit) {
        viewModel.stopCompletedEvents.collect { sessionId ->
            onNavigateToResult(sessionId)
        }
    }

    // Dim the screen to near-black while sleep tracking is active so the display effectively
    // goes off — saving battery and avoiding light disturbance during the night. Brightness is
    // restored automatically when tracking stops or the screen leaves composition. (Fully powering
    // the panel off requires device-admin privileges, which this app intentionally does not take.)
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(isTracking, activity) {
        val window = activity?.window
        if (isTracking && window != null) {
            window.attributes = window.attributes.apply { screenBrightness = 0.005f }
        }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // "Sleep aid" bar → ambient sounds library, with inline playback controls.
        val soundState by SoundPlayerService.state.collectAsStateWithLifecycle()
        SleepAidBar(
            state = soundState,
            onOpen = onNavigateToSounds,
            onPauseResume = {
                val action = if (soundState.isPaused) {
                    SoundPlayerService.ACTION_RESUME
                } else {
                    SoundPlayerService.ACTION_PAUSE
                }
                context.startService(
                    Intent(context, SoundPlayerService::class.java).setAction(action)
                )
            },
            onStop = {
                context.startService(
                    Intent(context, SoundPlayerService::class.java)
                        .setAction(SoundPlayerService.ACTION_STOP)
                )
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isTracking) {
            TrackingCircle(isTracking = true, startTime = trackingStartTime)
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            // Central alarm time picker → alarm settings.
            AlarmTimePicker(alarm = nextAlarm, onClick = onNavigateToAlarm)
            Spacer(modifier = Modifier.height(24.dp))

            // Record audio toggle.
            OptionToggleCard(
                icon = Icons.Default.GraphicEq,
                title = "Record audio",
                subtitle = "Capture snores, coughs & sleep talk",
                checked = recordAudio,
                onCheckedChange = { viewModel.setRecordAudioDuringTracking(it) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        val smartWakeActive = !isTracking && nextAlarm?.let { it.useSmartWake && it.wakeWindowMinutes > 0 } == true
        val startColor = when {
            isTracking -> MaterialTheme.colorScheme.error
            smartWakeActive -> Color(0xFFFF9800)
            else -> MaterialTheme.colorScheme.primary
        }
        Button(
            onClick = {
                if (isTracking) {
                    viewModel.requestStop()
                } else if (essentialPermissionsGranted && (!recordAudio || micGranted)) {
                    startWithBatteryGuard {
                        viewModel.startTracking()
                        Toast.makeText(context, "Tracking started", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    awaitingPermissionForStart.value = true
                    perms.launchMultiplePermissionRequest()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = startColor
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = if (isTracking) Icons.Default.Stop else Icons.Default.Nightlight,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isTracking) "Stop" else "Start",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (recentSessions.isNotEmpty()) {
            Text(
                text = "Recent Nights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recentSessions) { session ->
                    RecentSessionCard(
                        session = session,
                        onClick = { onNavigateToResult(session.id) }
                    )
                }
            }
        }
    }

    if (showMoodSelector) {
        MoodSelectorDialog(
            onSelect = { viewModel.selectMood(it) },
            onDismiss = { viewModel.dismissMoodSelector() }
        )
    }

    if (showLowBatteryDialog) {
        AlertDialog(
            onDismissRequest = {
                showLowBatteryDialog = false
                pendingStart = null
            },
            title = { Text("Low battery") },
            text = { Text("Connect your phone to a charger before you start.") },
            confirmButton = {
                TextButton(onClick = {
                    showLowBatteryDialog = false
                    val action = pendingStart
                    pendingStart = null
                    action?.invoke()
                }) {
                    Text("Start anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLowBatteryDialog = false
                    pendingStart = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SleepAidBar(
    state: SoundPlayerService.Companion.PlaybackState,
    onOpen: () -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = SleepSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sleep aid",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (state.isPlaying) {
                    val label = state.soundName?.replace("_", " ")?.replaceFirstChar { it.uppercase() }
                    Text(
                        text = if (state.isPaused) "Paused${label?.let { " · $it" } ?: ""}"
                        else "Playing${label?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.isPlaying) {
                IconButton(onClick = onPauseResume) {
                    Icon(
                        if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (state.isPaused) "Resume" else "Pause",
                        tint = SleepSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = SleepSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = SleepSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun AlarmTimePicker(alarm: AlarmConfig?, onClick: () -> Unit) {
    val timeText = alarm?.let { String.format(Locale.getDefault(), "%d:%02d", it.hour, it.minute) } ?: "--:--"
    val windowText = remember(alarm) {
        if (alarm == null) {
            "Tap to set your wake-up alarm"
        } else if (alarm.useSmartWake && alarm.wakeWindowMinutes > 0) {
            val startTotal = alarm.hour * 60 + alarm.minute - alarm.wakeWindowMinutes
            val norm = ((startTotal % 1440) + 1440) % 1440
            val startText = String.format(Locale.getDefault(), "%d:%02d", norm / 60, norm % 60)
            val endText = String.format(Locale.getDefault(), "%d:%02d", alarm.hour, alarm.minute)
            "Wake up easy between $startText – $endText"
        } else {
            "Alarm set — smart wake off"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Alarm,
                contentDescription = null,
                tint = SleepSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = timeText,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = windowText,
            style = MaterialTheme.typography.bodyMedium,
            color = SleepOnSurfaceVariant
        )
    }
}

@Composable
private fun OptionToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = SleepSecondary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SleepOnSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun TrackingCircle(isTracking: Boolean, startTime: Long?) {
    val infiniteTransition = rememberInfiniteTransition(label = "tracking")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    var elapsedText by remember { mutableStateOf("00:00:00") }

    LaunchedEffect(isTracking, startTime) {
        if (isTracking && startTime != null) {
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val hours = elapsed / 3_600_000
                val minutes = (elapsed % 3_600_000) / 60_000
                val seconds = (elapsed % 60_000) / 1_000
                elapsedText = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                delay(1000)
            }
        } else {
            elapsedText = "00:00:00"
        }
    }

    Box(
        modifier = Modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            if (isTracking) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SleepSecondary.copy(alpha = animatedAlpha * 0.3f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * 1.2f
                    ),
                    radius = radius * 1.2f,
                    center = center
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SleepSurfaceVariant, SleepSurface),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isTracking) Icons.Default.Nightlight else Icons.Default.NightsStay,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isTracking) SleepSecondary else SleepOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isTracking) {
                Text(
                    text = elapsedText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = SleepOnBackground
                )
                Text(
                    text = "Tracking",
                    style = MaterialTheme.typography.bodySmall,
                    color = SleepSecondary
                )
            } else {
                Text(
                    text = "Ready",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = SleepOnSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RecentSessionCard(
    session: SleepSession,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEE\nMMM d", Locale.getDefault()) }
    val dateText = remember(session.date) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = parser.parse(session.date)
            dateFormat.format(date ?: Date())
        } catch (_: Exception) {
            session.date
        }
    }
    val durationMs = ((session.endTime ?: (session.startTime + session.durationMinutes * 60_000L)) -
        session.startTime).coerceAtLeast(0L)

    Card(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${session.qualityScore}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    session.qualityScore >= 80 -> SleepScore
                    session.qualityScore >= 60 -> SleepSecondary
                    else -> SleepAwake
                }
            )
            Text(
                text = formatSleepDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MoodSelectorDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val moods = listOf(
        "great" to "😄",
        "good" to "🙂",
        "okay" to "😐",
        "bad" to "😔",
        "terrible" to "😫"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How did you sleep?") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                moods.forEach { (mood, emoji) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(mood) }
                            .padding(8.dp)
                    ) {
                        Text(text = emoji, fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mood.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        }
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
