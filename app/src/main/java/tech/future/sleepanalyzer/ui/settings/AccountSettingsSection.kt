package tech.future.sleepanalyzer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.future.sleepanalyzer.auth.AuthRepository
import tech.future.sleepanalyzer.auth.AuthState
import tech.future.sleepanalyzer.ui.theme.SleepSecondary

@Composable
internal fun AccountSettingsSection(
    signedIn: AuthState.SignedIn?,
    onSignIn: () -> Unit,
    onExport: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit
) {
    if (signedIn == null) {
        SettingsRow(
            icon = Icons.AutoMirrored.Filled.Login,
            title = "Sign in or create account",
            value = null,
            onClick = onSignIn
        )
        return
    }

    val identity = signedIn.displayName
        ?: signedIn.email
        ?: signedIn.phoneNumber
        ?: signedIn.uid
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            tint = SleepSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(identity, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (signedIn.provider == AuthRepository.LOCAL_PROVIDER) {
                    "Local account"
                } else {
                    "Signed in with ${signedIn.provider}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    SettingsRow(
        icon = Icons.Default.Download,
        title = if (signedIn.provider == AuthRepository.LOCAL_PROVIDER) {
            "Export account data"
        } else {
            "Request cloud data export"
        },
        value = null,
        onClick = onExport
    )
    if (signedIn.provider != AuthRepository.LOCAL_PROVIDER) {
        SettingsRow(Icons.AutoMirrored.Filled.Logout, "Sign out", null, onSignOut)
    }
    SettingsRow(Icons.Default.Delete, "Delete account", null, onDelete)
}
