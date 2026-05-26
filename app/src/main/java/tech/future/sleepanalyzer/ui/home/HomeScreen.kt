package tech.future.sleepanalyzer.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

@Composable
fun HomeScreen(
    onNavigateToStats: () -> Unit = {},
    onNavigateToTracker: () -> Unit = {},
    onNavigateToSounds: () -> Unit = {},
    onNavigateToRecorder: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val lastNight by viewModel.lastNight.collectAsStateWithLifecycle()
    val recentSessions by viewModel.recentSessions.collectAsStateWithLifecycle()
    val weeklyAvgScore by viewModel.weeklyAvgScore.collectAsStateWithLifecycle()
    val weeklyAvgDuration by viewModel.weeklyAvgDuration.collectAsStateWithLifecycle()
    val totalSessions by viewModel.totalSessions.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Greeting
        Text(
            getGreeting(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Here's your sleep summary",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Last night summary card
        LastNightCard(session = lastNight)
        Spacer(modifier = Modifier.height(16.dp))

        // Weekly stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickStatCard(
                title = "Avg Score",
                value = weeklyAvgScore?.let { "${it.toInt()}" } ?: "--",
                subtitle = "This week",
                icon = Icons.Default.Star,
                color = SleepScore,
                modifier = Modifier.weight(1f)
            )
            QuickStatCard(
                title = "Avg Duration",
                value = weeklyAvgDuration?.let {
                    val h = it.toInt() / 60
                    val m = it.toInt() % 60
                    "${h}h${m}m"
                } ?: "--",
                subtitle = "This week",
                icon = Icons.Default.Schedule,
                color = SleepSecondary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Weekly chart
        if (recentSessions.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sleep Quality Trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onNavigateToStats) {
                            Text("See all")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    WeeklyChart(sessions = recentSessions)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick actions
        Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard("Track Sleep", "🌙", Modifier.weight(1f), onClick = onNavigateToTracker)
            QuickActionCard("Sounds", "🎵", Modifier.weight(1f), onClick = onNavigateToSounds)
            QuickActionCard("Recorder", "🎙️", Modifier.weight(1f), onClick = onNavigateToRecorder)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Total sessions count
        Text(
            "Total nights tracked: $totalSessions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LastNightCard(session: SleepSession?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (session != null && session.qualityScore > 0) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Last Night", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val scoreColor = when {
                            session.qualityScore >= 80 -> SleepScore
                            session.qualityScore >= 60 -> SleepSecondary
                            else -> SleepAwake
                        }
                        Text(
                            "${session.qualityScore}",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor
                        )
                        Text(
                            "Quality Score",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Column {
                        val h = session.durationMinutes / 60
                        val m = session.durationMinutes % 60
                        DetailRow("Duration", "${h}h ${m}m")
                        DetailRow("Deep Sleep", "${session.deepSleepMinutes}min")
                        DetailRow("Interruptions", "${session.interruptions}")
                        if (session.moodAfter != null) {
                            DetailRow("Morning mood", session.moodAfter)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.NightsStay,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No sleep data for last night",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Start tracking tonight!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun QuickStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, title, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WeeklyChart(sessions: List<SleepSession>) {
    val maxScore = 100f
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        if (sessions.isEmpty()) return@Canvas
        val barWidth = size.width / (sessions.size * 2)
        val maxHeight = size.height - 20f

        sessions.forEachIndexed { index, session ->
            val barHeight = (session.qualityScore / maxScore) * maxHeight
            val x = (index * 2 + 0.5f) * barWidth
            val color = when {
                session.qualityScore >= 80 -> SleepScore
                session.qualityScore >= 60 -> SleepSecondary
                session.qualityScore >= 40 -> SleepLight
                else -> SleepAwake
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, maxHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 3)
            )
        }
    }
}

@Composable
fun QuickActionCard(title: String, emoji: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

fun getGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good Morning ☀️"
        hour < 17 -> "Good Afternoon 🌤️"
        hour < 21 -> "Good Evening 🌅"
        else -> "Good Night 🌙"
    }
}
