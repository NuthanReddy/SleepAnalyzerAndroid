package tech.future.sleepanalyzer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Static privacy notice (#18). Consolidates, in plain language, what data stays on the device,
 * what is uploaded when cloud sync is enabled, and how export/delete work — so the same policy the
 * Settings toggles hint at is available in one auditable place.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Your sleep data is yours",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "SleepAnalyzer is on-device first. Everything below reflects how the app actually " +
                    "handles your data today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PrivacySection(
                title = "Stays on your device",
                body = "Raw microphone audio, voice-enrollment samples, and detailed accelerometer motion " +
                    "never leave your phone. Audio is analyzed locally and saved privately under the app's " +
                    "storage; voice samples live in the app's voice_profiles folder."
            )
            PrivacySection(
                title = "Uploaded only if you turn on Cloud Sync",
                body = "With Cloud Sync enabled and signed in, the app syncs your profile, goals, nightly " +
                    "summaries, sessions, audio-event metadata (e.g. \u201Csnore detected\u201D counts and times), " +
                    "and notes. It never uploads the underlying audio recordings themselves."
            )
            PrivacySection(
                title = "Microphone use",
                body = "The mic is used only while you are recording or when \u201Cuse microphone for sleep " +
                    "staging\u201D is enabled during a tracked night. Signal-only staging listens for sound " +
                    "patterns (like snoring) to improve stage estimates without saving the audio."
            )
            PrivacySection(
                title = "Bedtime auto-detect",
                body = "If you enable automatic bedtime detection, the app watches only your screen state and " +
                    "device stillness to decide when to start tracking. No audio is used for this, and nothing " +
                    "about it is uploaded."
            )
            PrivacySection(
                title = "Export and delete",
                body = "You can request a full export or deletion of your account data from Settings at any " +
                    "time. Deletion signs you out on this device and submits a server-side request to remove " +
                    "your synced data."
            )
            PrivacySection(
                title = "No ads, no data sales",
                body = "Your data is never sold or shared with advertisers. It is used solely to provide the " +
                    "app's sleep-tracking features to you."
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
