package tech.future.sleepanalyzer.ui.programs

import android.app.Application
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.data.db.entity.SleepProgram
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.ui.theme.SleepScore

class ProgramsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SleepRepository(application)

    val programs: StateFlow<List<SleepProgram>> = repository.getAllPrograms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val existing = repository.getAllPrograms().first()
            if (existing.isEmpty()) {
                seedPrograms()
            }
        }
    }

    private suspend fun seedPrograms() {
        val defaultPrograms = listOf(
            SleepProgram(
                title = "Stress Relief for Better Sleep",
                description = "Learn techniques to manage stress and improve your sleep quality. Includes breathing exercises, progressive muscle relaxation, and mindfulness practices.",
                category = "stress_relief",
                steps = "[\"Day 1: Introduction to sleep-stress connection\",\"Day 2: 4-7-8 breathing technique\",\"Day 3: Progressive muscle relaxation\",\"Day 4: Guided visualization\",\"Day 5: Journaling before bed\",\"Day 6: Creating a worry list\",\"Day 7: Putting it all together\"]",
                durationDays = 7
            ),
            SleepProgram(
                title = "Bedroom Environment Hacks",
                description = "Optimize your sleep environment for maximum rest. From temperature to lighting, discover what makes the perfect bedroom for sleep.",
                category = "bedroom_hacks",
                steps = "[\"Day 1: Temperature optimization (65-68°F)\",\"Day 2: Light blocking techniques\",\"Day 3: Noise management\",\"Day 4: Mattress and pillow check\",\"Day 5: Remove electronics\",\"Day 6: Aromatherapy basics\",\"Day 7: Your ideal sleep sanctuary\"]",
                durationDays = 7
            ),
            SleepProgram(
                title = "Digital Detox Before Bed",
                description = "Break free from screen addiction before sleep. Learn to create healthy boundaries with technology for better rest.",
                category = "screen_use",
                steps = "[\"Day 1: Track your screen time\",\"Day 2: Set a screen curfew (1hr before bed)\",\"Day 3: Blue light filters and night mode\",\"Day 4: Replace scrolling with reading\",\"Day 5: No-phone bedroom challenge\",\"Day 6: Evening routine without screens\",\"Day 7: Maintaining your digital boundaries\"]",
                durationDays = 7
            ),
            SleepProgram(
                title = "Sleep Hygiene Fundamentals",
                description = "Master the basics of good sleep hygiene. Build habits that promote consistent, restful sleep every night.",
                category = "sleep_hygiene",
                steps = "[\"Day 1: Set a consistent sleep schedule\",\"Day 2: Limit caffeine after 2 PM\",\"Day 3: Exercise timing for sleep\",\"Day 4: Meal timing and sleep\",\"Day 5: Create a bedtime routine\",\"Day 6: Napping guidelines\",\"Day 7: Your personalized sleep plan\"]",
                durationDays = 7
            ),
            SleepProgram(
                title = "Deep Relaxation Techniques",
                description = "Discover powerful relaxation methods to help you fall asleep faster and stay asleep longer.",
                category = "relaxation",
                steps = "[\"Day 1: Body scan meditation\",\"Day 2: Deep breathing exercises\",\"Day 3: Yoga nidra for sleep\",\"Day 4: Autogenic training\",\"Day 5: Guided imagery\",\"Day 6: Self-hypnosis basics\",\"Day 7: Creating your relaxation toolkit\"]",
                durationDays = 7
            )
        )
        defaultPrograms.forEach { repository.insertProgram(it) }
    }

    fun startProgram(program: SleepProgram) {
        viewModelScope.launch {
            repository.updateProgram(
                program.copy(
                    isStarted = true,
                    startedAt = System.currentTimeMillis(),
                    currentDay = 1
                )
            )
        }
    }

    fun advanceDay(program: SleepProgram) {
        viewModelScope.launch {
            val nextDay = (program.currentDay + 1).coerceAtMost(program.durationDays)
            val completed = nextDay >= program.durationDays
            repository.updateProgram(program.copy(currentDay = nextDay, isCompleted = completed))
        }
    }
}

fun parseSteps(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    val trimmed = json.trim().removePrefix("[").removeSuffix("]")
    if (trimmed.isBlank()) return emptyList()

    val result = mutableListOf<String>()
    val sb = StringBuilder()
    var inString = false
    var escape = false

    for (c in trimmed) {
        when {
            escape -> {
                sb.append(
                    when (c) {
                        'n' -> '\n'
                        't' -> '\t'
                        'r' -> '\r'
                        else -> c
                    }
                )
                escape = false
            }
            c == '\\' -> escape = true
            c == '"' -> {
                if (inString) {
                    result += sb.toString()
                    sb.clear()
                }
                inString = !inString
            }
            inString -> sb.append(c)
        }
    }
    return result
}

@Composable
fun SleepProgramsScreen(
    viewModel: ProgramsViewModel = viewModel()
) {
    val programs by viewModel.programs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Sleep Programs",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Expert-guided programs for better sleep",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(programs, key = { it.id }) { program ->
                ProgramCard(program, viewModel)
            }
        }
    }
}

@Composable
fun ProgramCard(program: SleepProgram, viewModel: ProgramsViewModel) {
    val categoryEmoji = when (program.category) {
        "stress_relief" -> "🧘"
        "bedroom_hacks" -> "🛏️"
        "screen_use" -> "📱"
        "sleep_hygiene" -> "🌙"
        "relaxation" -> "🕯️"
        else -> "📋"
    }
    val steps = remember(program.steps) { parseSteps(program.steps) }
    var expanded by rememberSaveable(program.id) { mutableStateOf(program.isStarted || program.isCompleted) }
    val currentStepIndex = when {
        steps.isEmpty() -> -1
        !program.isStarted && !program.isCompleted -> -1
        program.isCompleted -> (program.durationDays - 1).coerceIn(0, steps.lastIndex)
        else -> (program.currentDay - 1).coerceAtLeast(0).coerceAtMost(steps.lastIndex)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = categoryEmoji, fontSize = 32.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = program.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${program.durationDays} days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = if (expanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = program.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (program.isStarted && !program.isCompleted) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { program.currentDay.toFloat() / program.durationDays },
                    modifier = Modifier.fillMaxWidth(),
                    color = SleepScore
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Day ${program.currentDay} of ${program.durationDays}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.advanceDay(program) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Complete Day ${program.currentDay}")
                }
            } else if (program.isCompleted) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✅ Completed!",
                    color = SleepScore,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        expanded = true
                        viewModel.startProgram(program)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Program")
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Program steps",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (steps.isEmpty()) {
                    Text(
                        text = "No steps available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        steps.forEachIndexed { index, stepText ->
                            val isCurrentStep = index == currentStepIndex
                            val isCompletedStep = program.isStarted && index < currentStepIndex
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isCurrentStep -> MaterialTheme.colorScheme.primaryContainer
                                        isCompletedStep -> MaterialTheme.colorScheme.surface
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Day ${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isCurrentStep) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stepText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when {
                                            isCurrentStep -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
