package tech.future.sleepanalyzer.ui.onboarding

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun VoiceEnrollScreen(
    onBack: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val recording by viewModel.enrollmentRecording.collectAsStateWithLifecycle()
    val progress by viewModel.enrollmentProgress.collectAsStateWithLifecycle()
    val result by viewModel.enrollmentResult.collectAsStateWithLifecycle()
    val error by viewModel.enrollmentError.collectAsStateWithLifecycle()
    val microphonePermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Setup") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.cancelEnrollment()
                            onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        VoiceEnrollmentContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            title = "Speak naturally for 5 seconds",
            subtitle = "Re-enroll your voice profile anytime from Settings.",
            recording = recording,
            progress = progress,
            resultAvailable = result != null,
            error = error,
            hasMicrophonePermission = microphonePermissionState.status.isGranted,
            onStart = viewModel::startEnrollment,
            onCancel = viewModel::cancelEnrollment,
            onRequestPermission = { microphonePermissionState.launchPermissionRequest() },
            footer = {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !recording
                ) {
                    Text(if (result != null) "Done" else "Back")
                }
            }
        )
    }
}

@Composable
internal fun VoiceEnrollmentContent(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    recording: Boolean,
    progress: Float,
    resultAvailable: Boolean,
    error: String?,
    hasMicrophonePermission: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRequestPermission: () -> Unit,
    footer: @Composable (ColumnScope.() -> Unit)? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = when {
            recording || resultAvailable -> progress.coerceIn(0f, 1f)
            else -> 0f
        },
        label = "voice-enroll-progress"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(220.dp),
                strokeWidth = 12.dp
            )
            FilledIconButton(
                onClick = {
                    when {
                        recording -> onCancel()
                        !hasMicrophonePermission -> onRequestPermission()
                        else -> onStart()
                    }
                },
                modifier = Modifier.size(88.dp)
            ) {
                Icon(
                    imageVector = when {
                        resultAvailable -> Icons.Default.Check
                        recording -> Icons.Default.Stop
                        hasMicrophonePermission -> Icons.Default.FiberManualRecord
                        else -> Icons.Default.Mic
                    },
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = when {
                recording -> "Recording… ${(animatedProgress * 5f).coerceAtMost(5f).let { String.format("%.1f", it) }}s / 5.0s"
                resultAvailable -> "Voice profile saved on this device."
                !hasMicrophonePermission -> "Microphone access is required to enroll your voice."
                else -> "Tap the record button and speak naturally."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your sample stays on-device in the app's private storage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    recording -> onCancel()
                    !hasMicrophonePermission -> onRequestPermission()
                    else -> onStart()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    recording -> "Cancel enrollment"
                    !hasMicrophonePermission -> "Grant microphone"
                    resultAvailable -> "Record again"
                    else -> "Start 5-second sample"
                }
            )
        }

        footer?.let {
            Spacer(modifier = Modifier.height(12.dp))
            it()
        }
    }
}
