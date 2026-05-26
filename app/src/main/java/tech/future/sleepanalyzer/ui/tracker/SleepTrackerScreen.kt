package tech.future.sleepanalyzer.ui.tracker

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
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.ui.theme.SleepAwake
import tech.future.sleepanalyzer.ui.theme.SleepOnBackground
import tech.future.sleepanalyzer.ui.theme.SleepOnSurfaceVariant
import tech.future.sleepanalyzer.ui.theme.SleepScore
import tech.future.sleepanalyzer.ui.theme.SleepSecondary
import tech.future.sleepanalyzer.ui.theme.SleepSurface
import tech.future.sleepanalyzer.ui.theme.SleepSurfaceVariant
import tech.future.sleepanalyzer.util.PermissionsUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SleepTrackerScreen(
    onNavigateToResult: (Long) -> Unit = {},
    viewModel: SleepTrackerViewModel = viewModel()
) {
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val trackingStartTime by viewModel.trackingStartTime.collectAsStateWithLifecycle()
    val recentSessions by viewModel.recentSessions.collectAsStateWithLifecycle()
    val showMoodSelector by viewModel.showMoodSelector.collectAsStateWithLifecycle()
    val trackerPermissions = PermissionsUtil.trackerPermissions()
    val perms = rememberMultiplePermissionsState(trackerPermissions)
    val trackerPermissionsGranted = trackerPermissions.isEmpty() || perms.allPermissionsGranted
    var awaitingPermissionForStart by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.stopCompletedEvents.collect { sessionId ->
            onNavigateToResult(sessionId)
        }
    }

    // Auto-resume start once permissions are granted (avoids double-tap UX). See backlog #24.
    LaunchedEffect(trackerPermissionsGranted, awaitingPermissionForStart) {
        if (awaitingPermissionForStart && trackerPermissionsGranted && !isTracking) {
            awaitingPermissionForStart = false
            viewModel.startTracking()
            Toast.makeText(context, "Tracking started", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sleep Tracker",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isTracking) "Tracking your sleep..." else "Ready to track your sleep",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(32.dp))

        TrackingCircle(
            isTracking = isTracking,
            startTime = trackingStartTime
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isTracking) {
            OutlinedButton(
                onClick = { viewModel.showMoodBefore() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Mood, contentDescription = "Mood", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("How are you feeling?")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                if (isTracking) {
                    viewModel.requestStop()
                } else if (trackerPermissionsGranted) {
                    viewModel.startTracking()
                    Toast.makeText(context, "Tracking started", Toast.LENGTH_SHORT).show()
                } else {
                    awaitingPermissionForStart = true
                    perms.launchMultiplePermissionRequest()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
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
                text = if (isTracking) "Stop Tracking" else "Start Sleep Tracking",
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
    val hours = session.durationMinutes / 60
    val mins = session.durationMinutes % 60

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
                text = "${hours}h ${mins}m",
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
        title = { Text("How are you feeling?") },
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
