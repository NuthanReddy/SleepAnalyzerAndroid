package tech.future.sleepanalyzer.ui.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tech.future.sleepanalyzer.ui.theme.SleepAwake
import tech.future.sleepanalyzer.ui.theme.SleepScore
import tech.future.sleepanalyzer.ui.theme.SleepSecondary
import tech.future.sleepanalyzer.ui.theme.SleepSurface
import kotlin.random.Random

enum class GameState { WAITING, READY, GO, RESULT, TOO_EARLY }

@Composable
fun AlertnessGameScreen() {
    var gameState by remember { mutableStateOf(GameState.WAITING) }
    var reactionTime by remember { mutableLongStateOf(0L) }
    var goTime by remember { mutableLongStateOf(0L) }
    var attempts by remember { mutableStateOf<List<Long>>(emptyList()) }
    var round by remember { mutableIntStateOf(0) }
    val totalRounds = 5

    val bgColor by animateColorAsState(
        targetValue = when (gameState) {
            GameState.WAITING -> SleepSurface
            GameState.READY -> SleepAwake.copy(alpha = 0.3f)
            GameState.GO -> SleepScore.copy(alpha = 0.5f)
            GameState.RESULT -> SleepSurface
            GameState.TOO_EARLY -> SleepAwake.copy(alpha = 0.5f)
        },
        label = "bg"
    )

    LaunchedEffect(gameState) {
        if (gameState == GameState.READY) {
            val waitTime = Random.nextLong(1500, 4000)
            delay(waitTime)
            if (gameState == GameState.READY) {
                goTime = System.currentTimeMillis()
                gameState = GameState.GO
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(enabled = gameState != GameState.WAITING && gameState != GameState.RESULT) {
                when (gameState) {
                    GameState.READY -> {
                        gameState = GameState.TOO_EARLY
                    }

                    GameState.GO -> {
                        reactionTime = System.currentTimeMillis() - goTime
                        attempts = attempts + reactionTime
                        round++
                        gameState = if (round >= totalRounds) GameState.RESULT else GameState.WAITING
                    }

                    GameState.TOO_EARLY -> {
                        gameState = GameState.WAITING
                    }

                    GameState.WAITING, GameState.RESULT -> Unit
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "⚡ Alertness Test",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (gameState) {
                GameState.WAITING -> {
                    Text(
                        text = "Round ${round + 1} of $totalRounds",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { gameState = GameState.READY },
                        modifier = Modifier.size(140.dp),
                        shape = CircleShape
                    ) {
                        Text("START", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tap when the screen turns GREEN",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                GameState.READY -> {
                    Text(
                        text = "Wait for green...",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleepAwake
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Don't tap yet!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                GameState.GO -> {
                    Text(
                        text = "TAP NOW!",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleepScore
                    )
                }

                GameState.TOO_EARLY -> {
                    Text(
                        text = "Too early! 😅",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleepAwake
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap anywhere to try again",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                GameState.RESULT -> {
                    val avgTime = if (attempts.isNotEmpty()) attempts.average().toLong() else 0L
                    val bestTime = attempts.minOrNull() ?: 0L
                    val alertness = when {
                        avgTime < 250 -> "Excellent! ⚡"
                        avgTime < 350 -> "Good 👍"
                        avgTime < 450 -> "Average 😐"
                        else -> "Sleepy 😴"
                    }

                    Text(
                        text = alertness,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = SleepScore
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Average: ${avgTime}ms",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = SleepSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Best: ${bestTime}ms",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SleepScore
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Attempts: ${attempts.joinToString(", ") { "${it}ms" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        attempts = emptyList()
                        round = 0
                        gameState = GameState.WAITING
                    }) {
                        Text("Play Again")
                    }
                }
            }
        }
    }
}
