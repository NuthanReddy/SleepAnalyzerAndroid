package tech.future.sleepanalyzer.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.ui.theme.*
import tech.future.sleepanalyzer.util.formatSleepDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedStatsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: StatsViewModel = viewModel()
) {
    val timeRange by viewModel.timeRange.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val avgScore by viewModel.averageScore.collectAsStateWithLifecycle()
    val avgDuration by viewModel.averageDurationMs.collectAsStateWithLifecycle()
    val bestNight by viewModel.bestNight.collectAsStateWithLifecycle()
    val worstNight by viewModel.worstNight.collectAsStateWithLifecycle()
    val avgDeepSleep by viewModel.averageDeepSleepMs.collectAsStateWithLifecycle()
    val avgInterruptions by viewModel.averageInterruptions.collectAsStateWithLifecycle()
    val stepsStats by viewModel.stepsStats.collectAsStateWithLifecycle()
    val durationTrend by viewModel.durationTrend.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
            Text(
                text = "Sleep Statistics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Time range selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("day" to "Days", "week" to "Weeks", "month" to "Months", "all" to "All").forEach { (id, label) ->
                    FilterChip(
                        selected = timeRange == id,
                        onClick = { viewModel.setTimeRange(id) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Summary cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("Avg Score", avgScore?.let { "${it.toInt()}" } ?: "--", SleepScore, Modifier.weight(1f))
                StatCard(
                    "Avg Duration",
                    avgDuration?.let { formatSleepDuration(it) } ?: "--",
                    SleepSecondary,
                    Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("Avg Deep Sleep", formatSleepDuration(avgDeepSleep), SleepDeep, Modifier.weight(1f))
                StatCard("Avg Interruptions", String.format("%.1f", avgInterruptions), SleepAwake, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    "Avg Daily Steps",
                    if (stepsStats.daysWithData > 0) "%,d".format(stepsStats.dailyAverage) else "--",
                    SleepScore,
                    Modifier.weight(1f)
                )
                StatCard(
                    "Total Steps",
                    if (stepsStats.total > 0) "%,d".format(stepsStats.total) else "--",
                    SleepSecondary,
                    Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Score trend chart
            if (sessions.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Quality Score Trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        ScoreTrendChart(sessions = sessions)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Duration chart
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Duration Trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                            DurationChart(buckets = durationTrend)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Best/Worst nights
            bestNight?.let { best ->
                HighlightCard("Best Night 🌟", best)
            }
            Spacer(modifier = Modifier.height(8.dp))
            worstNight?.let { worst ->
                HighlightCard("Needs Improvement 💪", worst)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
}

@Composable
fun StatCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ScoreTrendChart(sessions: List<SleepSession>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        if (sessions.size < 2) return@Canvas
        val maxScore = 100f
        val padding = 8.dp.toPx()
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2
        val stepX = chartWidth / (sessions.size - 1)

        val path = Path()
        sessions.forEachIndexed { index, session ->
            val x = padding + index * stepX
            val y = padding + chartHeight * (1 - session.qualityScore / maxScore)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, SleepSecondary, style = Stroke(width = 3.dp.toPx()))

        // Draw dots
        sessions.forEachIndexed { index, session ->
            val x = padding + index * stepX
            val y = padding + chartHeight * (1 - session.qualityScore / maxScore)
            drawCircle(SleepSecondary, radius = 4.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
fun DurationChart(buckets: List<DurationBucket>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        if (buckets.isEmpty()) return@Canvas
        val durations = buckets.map { it.averageDurationMs }
        val maxDuration = (durations.maxOrNull() ?: 1L).coerceAtLeast(1L)
        val barWidth = size.width / (buckets.size * 2)
        val maxHeight = size.height

        durations.forEachIndexed { index, durationMs ->
            val barHeight = (durationMs.toFloat() / maxDuration) * maxHeight
            val x = (index * 2 + 0.5f) * barWidth
            drawRoundRect(
                color = SleepTertiary,
                topLeft = Offset(x, maxHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 3)
            )
        }
    }
}

/** Real elapsed time for a session, keeping sub-minute precision the stored minute column loses. */
private fun sessionElapsedMs(session: SleepSession): Long =
    ((session.endTime ?: (session.startTime + session.durationMinutes * 60_000L)) - session.startTime)
        .coerceAtLeast(0L)

@Composable
fun HighlightCard(title: String, session: SleepSession) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(session.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Score: ${session.qualityScore}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (session.qualityScore >= 80) SleepScore else SleepAwake
                )
                val durationMs = sessionElapsedMs(session)
                Text(formatSleepDuration(durationMs), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
