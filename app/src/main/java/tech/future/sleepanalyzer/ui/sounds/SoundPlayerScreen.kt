package tech.future.sleepanalyzer.ui.sounds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.future.sleepanalyzer.sounds.SoundItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPlayerScreen(
    categoryId: String,
    onBack: () -> Unit,
    viewModel: SoundsViewModel = viewModel()
) {
    val category = remember { viewModel.getCategoryById(categoryId) }
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentSound by viewModel.currentSound.collectAsStateWithLifecycle()
    val timerMinutes by viewModel.timerMinutes.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.name ?: "Sounds") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Category header
            category?.let {
                Text(it.icon, fontSize = 48.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(it.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Volume slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeDown, "Volume", modifier = Modifier.size(20.dp))
                Slider(
                    value = volume,
                    onValueChange = { viewModel.setVolume(it) },
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.VolumeUp, "Volume", modifier = Modifier.size(20.dp))
            }

            // Timer selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(15, 30, 45, 60, 90).forEach { min ->
                    FilterChip(
                        selected = timerMinutes == min,
                        onClick = { viewModel.setTimer(min) },
                        label = { Text("${min}m") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sound list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(category?.sounds ?: emptyList()) { sound ->
                    SoundItemCard(
                        sound = sound,
                        isPlaying = isPlaying && currentSound == sound.id,
                        onPlay = {
                            if (isPlaying && currentSound == sound.id) {
                                viewModel.stopSound()
                            } else {
                                viewModel.playSound(sound)
                                viewModel.setTimer(timerMinutes)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SoundItemCard(
    sound: SoundItem,
    isPlaying: Boolean,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(sound.icon, fontSize = 28.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    sound.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    sound.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPlay) {
                Icon(
                    if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
