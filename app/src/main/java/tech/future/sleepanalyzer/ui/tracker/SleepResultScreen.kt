package tech.future.sleepanalyzer.ui.tracker

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.db.entity.WearableSample
import tech.future.sleepanalyzer.di.ServiceLocator
import tech.future.sleepanalyzer.sleep.SleepStage
import tech.future.sleepanalyzer.sleep.VendorStageSegment
import tech.future.sleepanalyzer.ui.theme.SleepAwake
import tech.future.sleepanalyzer.ui.theme.SleepDeep
import tech.future.sleepanalyzer.ui.theme.SleepLight
import tech.future.sleepanalyzer.ui.theme.SleepOnSurfaceVariant
import tech.future.sleepanalyzer.ui.theme.SleepREM
import tech.future.sleepanalyzer.ui.theme.SleepScore
import tech.future.sleepanalyzer.ui.theme.SleepSecondary
import tech.future.sleepanalyzer.ui.theme.SleepSurfaceVariant
import tech.future.sleepanalyzer.wearables.WearableMetric

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepResultScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: SleepResultViewModel = viewModel(
        factory = SleepResultViewModelFactory(sessionId)
    )
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val repository = remember { ServiceLocator.repository }
    val heartRateSamples by repository.observeWearableSamples(sessionId, WearableMetric.HEART_RATE.name)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var vendorStageSegments by remember { mutableStateOf<List<VendorStageSegment>>(emptyList()) }

    LaunchedEffect(session?.id, session?.startTime, session?.endTime) {
        val currentSession = session
        vendorStageSegments = if (currentSession == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                repository.getVendorStageSegmentsForSession(
                    currentSession.startTime,
                    currentSession.endTime ?: System.currentTimeMillis()
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        session?.let { currentSession ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                ScoreCircle(score = currentSession.qualityScore)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = getScoreLabel(currentSession.qualityScore),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(24.dp))

                val hours = currentSession.durationMinutes / 60
                val mins = currentSession.durationMinutes % 60
                SummaryCard("Duration", "${hours}h ${mins}m")
                Spacer(modifier = Modifier.height(12.dp))

                if (vendorStageSegments.isNotEmpty()) {
                    VendorStagesBadge(
                        sourceLabel = vendorStageSegments.firstOrNull { !it.deviceId.isNullOrBlank() }?.deviceId
                            ?: "Health Connect",
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                SleepStagesCard(
                    session = currentSession,
                    vendorStageSegments = vendorStageSegments,
                    sessionEndMs = currentSession.endTime ?: System.currentTimeMillis()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard("Interruptions", "${currentSession.interruptions}", Modifier.weight(1f))
                    SummaryCard(
                        "Mood",
                        "${currentSession.moodBefore ?: "-"} → ${currentSession.moodAfter ?: "-"}",
                        Modifier.weight(1f)
                    )
                }
                if (heartRateSamples.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HeartRateCard(heartRateSamples)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun ScoreCircle(score: Int) {
    var animationPlayed by remember { mutableStateOf(false) }
    val animatedScore by animateFloatAsState(
        targetValue = if (animationPlayed) score.toFloat() else 0f,
        animationSpec = tween(1500),
        label = "score"
    )
    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    val scoreColor = when {
        score >= 80 -> SleepScore
        score >= 60 -> SleepSecondary
        score >= 40 -> SleepLight
        else -> SleepAwake
    }

    Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset(
                (size.width - radius * 2) / 2,
                (size.height - radius * 2) / 2
            )
            drawArc(
                color = SleepSurfaceVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = scoreColor,
                startAngle = -90f,
                sweepAngle = 360f * (animatedScore / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${animatedScore.toInt()}",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )
            Text(
                text = "Quality Score",
                style = MaterialTheme.typography.bodySmall,
                color = SleepOnSurfaceVariant
            )
        }
    }
}

@Composable
fun SleepStagesCard(
    session: SleepSession,
    vendorStageSegments: List<VendorStageSegment> = emptyList(),
    sessionEndMs: Long = session.endTime ?: System.currentTimeMillis()
) {
    val breakdown = remember(session, vendorStageSegments, sessionEndMs) {
        resolveStageBreakdown(session, vendorStageSegments, sessionEndMs)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Sleep Stages",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                val stages = listOf(
                    breakdown.deepMs to SleepDeep,
                    breakdown.lightMs to SleepLight,
                    breakdown.remMs to SleepREM,
                    breakdown.awakeMs to SleepAwake
                )
                stages.forEach { (durationMs, color) ->
                    if (durationMs > 0L) {
                        Box(
                            modifier = Modifier
                                .weight(durationMs.toFloat() / breakdown.totalMs.toFloat())
                                .fillMaxHeight()
                                .padding(horizontal = 1.dp)
                                .background(color = color, shape = RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            StageRow("Deep Sleep", breakdown.deepMs, SleepDeep)
            StageRow("Light Sleep", breakdown.lightMs, SleepLight)
            StageRow("REM Sleep", breakdown.remMs, SleepREM)
            StageRow("Awake", breakdown.awakeMs, SleepAwake)
        }
    }
}

@Composable
private fun VendorStagesBadge(sourceLabel: String, modifier: Modifier = Modifier) {
    SuggestionChip(
        onClick = {},
        label = { Text("Stages from $sourceLabel") },
        modifier = modifier
    )
}

@Composable
fun StageRow(label: String, durationMs: Long, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class StageBreakdown(
    val deepMs: Long,
    val lightMs: Long,
    val remMs: Long,
    val awakeMs: Long
) {
    val totalMs: Long = (deepMs + lightMs + remMs + awakeMs).coerceAtLeast(1L)
}

private fun resolveStageBreakdown(
    session: SleepSession,
    vendorStageSegments: List<VendorStageSegment>,
    sessionEndMs: Long
): StageBreakdown {
    val heuristic = StageBreakdown(
        deepMs = session.deepSleepMinutes * MILLIS_PER_MINUTE,
        lightMs = session.lightSleepMinutes * MILLIS_PER_MINUTE,
        remMs = session.remSleepMinutes * MILLIS_PER_MINUTE,
        awakeMs = session.awakeMinutes * MILLIS_PER_MINUTE
    )
    if (vendorStageSegments.isEmpty()) return heuristic

    val stageDurations = mutableMapOf<SleepStage, Long>()
    var coveredDurationMs = 0L
    vendorStageSegments.forEach { segment ->
        val overlapMs = (minOf(segment.endMs, sessionEndMs) - maxOf(segment.startMs, session.startTime))
            .coerceAtLeast(0L)
        if (overlapMs <= 0L) return@forEach
        coveredDurationMs += overlapMs
        stageDurations[segment.stage] = stageDurations.getOrDefault(segment.stage, 0L) + overlapMs
    }

    val sessionDurationMs = (sessionEndMs - session.startTime).coerceAtLeast(1L)
    if (coveredDurationMs * 2 <= sessionDurationMs) return heuristic

    return StageBreakdown(
        deepMs = stageDurations.getOrDefault(SleepStage.DEEP, 0L),
        lightMs = stageDurations.getOrDefault(SleepStage.LIGHT, 0L),
        remMs = stageDurations.getOrDefault(SleepStage.REM, 0L),
        awakeMs = stageDurations.getOrDefault(SleepStage.AWAKE, 0L)
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = (durationMs / MILLIS_PER_MINUTE).toInt()
    return "${totalMinutes / 60}h ${totalMinutes % 60}m"
}

@Composable
fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HeartRateCard(samples: List<WearableSample>) {
    if (samples.isEmpty()) return  // Defensive: callers gate this but make the function safe to call anyway.
    val minBpm = samples.minOf { it.value }
    val maxBpm = samples.maxOf { it.value }
    val avgBpm = samples.map { it.value.toDouble() }.average().toFloat()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Heart rate",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                if (samples.isEmpty()) return@Canvas
                val chartPadding = 8.dp.toPx()
                val chartWidth = size.width - chartPadding * 2f
                val chartHeight = size.height - chartPadding * 2f
                val singleValue = maxBpm == minBpm
                val path = Path()
                val stepX = if (samples.size > 1) chartWidth / (samples.size - 1) else 0f

                samples.forEachIndexed { index, sample ->
                    val x = chartPadding + index * stepX
                    val normalized = if (singleValue) 0.5f else (sample.value - minBpm) / (maxBpm - minBpm)
                    val y = chartPadding + chartHeight * (1f - normalized)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                if (samples.size > 1) {
                    drawPath(
                        path = path,
                        color = SleepSecondary,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                samples.forEachIndexed { index, sample ->
                    val x = chartPadding + index * stepX
                    val normalized = if (singleValue) 0.5f else (sample.value - minBpm) / (maxBpm - minBpm)
                    val y = chartPadding + chartHeight * (1f - normalized)
                    drawCircle(
                        color = SleepSecondary,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeartRateStat(label = "Min", value = "${minBpm.toInt()} bpm", modifier = Modifier.weight(1f))
                HeartRateStat(label = "Avg", value = "${avgBpm.toInt()} bpm", modifier = Modifier.weight(1f))
                HeartRateStat(label = "Max", value = "${maxBpm.toInt()} bpm", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeartRateStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

private const val MILLIS_PER_MINUTE = 60_000L

fun getScoreLabel(score: Int): String = when {
    score >= 90 -> "Excellent Sleep! 🌟"
    score >= 80 -> "Great Sleep! 😊"
    score >= 70 -> "Good Sleep 👍"
    score >= 60 -> "Fair Sleep 😐"
    score >= 40 -> "Poor Sleep 😔"
    else -> "Very Poor Sleep 😫"
}
