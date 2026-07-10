package tech.future.sleepanalyzer.ui.recorder

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import tech.future.sleepanalyzer.audio.Attribution
import tech.future.sleepanalyzer.data.db.entity.AudioRecording
import tech.future.sleepanalyzer.ui.theme.SleepAwake
import tech.future.sleepanalyzer.ui.theme.SleepDeep
import tech.future.sleepanalyzer.ui.theme.SleepLight
import tech.future.sleepanalyzer.ui.theme.SleepOnSurfaceVariant
import tech.future.sleepanalyzer.ui.theme.SleepSecondary
import tech.future.sleepanalyzer.util.PermissionsUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecorderScreen(
    viewModel: RecorderViewModel = viewModel()
) {
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordings by viewModel.recentRecordings.collectAsStateWithLifecycle()
    val recordingSessions by viewModel.recordingSessions.collectAsStateWithLifecycle()
    val playingId by viewModel.playingRecordingId.collectAsStateWithLifecycle()
    val voiceIsolationEnabled by viewModel.voiceIsolationEnabled.collectAsStateWithLifecycle()
    val snoreStats by viewModel.snoreStats.collectAsStateWithLifecycle()
    val coughStats by viewModel.coughStats.collectAsStateWithLifecycle()
    val talkStats by viewModel.talkStats.collectAsStateWithLifecycle()
    val noiseCount by viewModel.noiseCount.collectAsStateWithLifecycle()
    val transcriptionState by viewModel.transcriptionState.collectAsStateWithLifecycle()
    val perms = rememberMultiplePermissionsState(PermissionsUtil.recorderPermissions())
    var awaitingPermissionForStart by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Auto-resume the recording start once the user grants permission, so they don't have to
    // tap "Start" twice (first to trigger the prompt, again to actually start). See backlog #24.
    LaunchedEffect(perms.allPermissionsGranted, awaitingPermissionForStart) {
        if (awaitingPermissionForStart && perms.allPermissionsGranted && !isRecording) {
            awaitingPermissionForStart = false
            viewModel.startRecording()
            Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Sleep Recorder",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Track snoring, coughing & sleep sounds",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        RecordingIndicator(isRecording = isRecording)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isRecording) {
                    viewModel.stopRecording()
                    Toast.makeText(context, "Recording stopped", Toast.LENGTH_SHORT).show()
                } else if (perms.allPermissionsGranted) {
                    viewModel.startRecording()
                    Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
                } else {
                    awaitingPermissionForStart = true
                    perms.launchMultiplePermissionRequest()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isRecording) "Stop Recording" else "Start Recording",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        VoiceIsolationPill(enabled = voiceIsolationEnabled)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Attribution breakdown",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AttributionStatCard(
                label = "🫁 Snore",
                stats = snoreStats,
                color = SleepDeep,
                voiceIsolationEnabled = voiceIsolationEnabled,
                modifier = Modifier.weight(1f)
            )
            AttributionStatCard(
                label = "😷 Cough",
                stats = coughStats,
                color = SleepAwake,
                voiceIsolationEnabled = voiceIsolationEnabled,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AttributionStatCard(
                label = "💬 Talk",
                stats = talkStats,
                color = SleepSecondary,
                voiceIsolationEnabled = voiceIsolationEnabled,
                modifier = Modifier.weight(1f)
            )
            StatChip("🔊 Noise", noiseCount.toString(), SleepLight, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Recordings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No recordings yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recordingSessions.forEach { session ->
                    item(key = "header_${session.key}") {
                        Text(
                            text = "${session.title}  ·  ${session.recordings.size}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(session.recordings, key = { it.id }) { recording ->
                        RecordingItem(
                            recording = recording,
                            isPlaying = playingId == recording.id,
                            voiceIsolationEnabled = voiceIsolationEnabled,
                            transcriptionState = transcriptionState[recording.id],
                            onPlay = {
                                if (playingId == recording.id) viewModel.stopPlayback()
                                else viewModel.playRecording(recording)
                            },
                            onTranscribe = { viewModel.transcribe(recording) },
                            onDelete = { viewModel.deleteRecording(recording) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingIndicator(isRecording: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val barHeights = List(20) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(500 + index * 50, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$index"
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val barWidth = size.width / (barHeights.size * 2)
        val maxHeight = size.height

        barHeights.forEachIndexed { index, animatable ->
            val height = if (isRecording) maxHeight * animatable.value else maxHeight * 0.1f
            val x = (index * 2 + 0.5f) * barWidth
            val color = if (isRecording) SleepSecondary else SleepOnSurfaceVariant

            drawRoundRect(
                color = color,
                topLeft = Offset(x, (maxHeight - height) / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}

@Composable
fun VoiceIsolationPill(enabled: Boolean) {
    val containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = "Voice isolation: ${if (enabled) "on" else "off"}",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
fun AttributionStatCard(
    label: String,
    stats: RecordingAttributionStats,
    color: Color,
    voiceIsolationEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stats.totalCount.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "You ${stats.userCount} • Partner ${stats.partnerCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (voiceIsolationEnabled) "Unknown ${stats.unknownCount}" else "Unknown ${stats.totalCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun RecordingItem(
    recording: AudioRecording,
    isPlaying: Boolean,
    voiceIsolationEnabled: Boolean,
    transcriptionState: TranscriptionUiState?,
    onPlay: () -> Unit,
    onTranscribe: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val typeIcon = when (recording.type) {
        "snore" -> "🫁"
        "cough" -> "😷"
        "talk" -> "💬"
        "noise" -> "🔊"
        else -> "🎵"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(typeIcon, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            recording.type.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AttributionBadge(
                            attribution = Attribution.fromKey(recording.attributedTo),
                            enabled = voiceIsolationEnabled
                        )
                    }
                    Text(
                        "${timeFormat.format(Date(recording.startTime))} • ${recording.durationSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPlay) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (recording.type == "talk") {
                TranscriptSection(
                    recording = recording,
                    state = transcriptionState,
                    onTranscribe = onTranscribe
                )
            }
        }
    }
}

@Composable
private fun TranscriptSection(
    recording: AudioRecording,
    state: TranscriptionUiState?,
    onTranscribe: () -> Unit
) {
    val transcript = recording.transcript
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 12.dp, bottom = 12.dp)
    ) {
        when {
            state is TranscriptionUiState.Running -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text(
                    "Transcribing… (first run downloads the speech model)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            !transcript.isNullOrBlank() -> Text(
                "\u201C$transcript\u201D",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            transcript == "" -> Text(
                "No speech detected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Column {
                if (state is TranscriptionUiState.Error) {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                TextButton(onClick = onTranscribe, contentPadding = PaddingValues(0.dp)) {
                    Icon(
                        Icons.Default.Subtitles,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state is TranscriptionUiState.Error) "Retry transcription" else "Transcribe")
                }
            }
        }
    }
}

@Composable
fun AttributionBadge(attribution: Attribution, enabled: Boolean) {
    val (label, containerColor, contentColor) = when (attribution) {
        Attribution.USER -> Triple(
            "🙂 You",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        Attribution.PARTNER -> Triple(
            "👥 Partner",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        Attribution.UNKNOWN -> Triple(
            "❔ Unknown",
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        color = if (enabled) containerColor else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
