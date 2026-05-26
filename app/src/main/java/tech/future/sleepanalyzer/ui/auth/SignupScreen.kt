package tech.future.sleepanalyzer.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.future.sleepanalyzer.auth.FIREBASE_NOT_CONFIGURED_MESSAGE

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: SignupViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val signupState by viewModel.signupState.collectAsStateWithLifecycle()
    val isConfigured = viewModel.isConfigured

    LaunchedEffect(signupState) {
        when (signupState) {
            is SignupUiState.SignedIn -> {
                viewModel.clearSignupState()
                onSignedIn()
            }
            SignupUiState.Guest -> {
                viewModel.clearSignupState()
                onBack()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign up") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        SignupPanel(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            title = "Sign in to sync across devices",
            subtitle = if (isConfigured) {
                "Phone is the primary sign-in path for India (+91) and North America (+1). Google works everywhere. You can always continue as a guest."
            } else {
                "This build has no cloud sign-in configured, but you can still create a local account so your profile, sessions, and notes stay tied to a stable identity on this device."
            },
            signupState = signupState,
            isConfigured = isConfigured,
            onStartPhone = { phone -> activity?.let { viewModel.startPhoneVerification(it, phone) } },
            onConfirmCode = viewModel::confirmPhoneCode,
            onGoogleSignIn = { activity?.let(viewModel::signInWithGoogle) },
            onLocalSignUp = viewModel::signUpLocal,
            onContinueAsGuest = viewModel::continueAsGuest,
            phoneUnavailableMessage = if (activity == null) {
                "Sign-in requires an activity context."
            } else null
        )
    }
}

@Composable
fun SignupPanel(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    signupState: SignupUiState,
    isConfigured: Boolean,
    onStartPhone: (String) -> Unit,
    onConfirmCode: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onLocalSignUp: (String, String?) -> Unit,
    onContinueAsGuest: () -> Unit,
    onBack: (() -> Unit)? = null,
    phoneUnavailableMessage: String? = null
) {
    var country by rememberSaveable { mutableStateOf(PhoneCountry.India) }
    var otherCountryCode by rememberSaveable { mutableStateOf("") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var localDisplayName by rememberSaveable { mutableStateOf("") }
    var localEmail by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val busy = signupState is SignupUiState.Loading
    val awaitingCode = signupState as? SignupUiState.AwaitingCode
    val message = localError ?: when (signupState) {
        is SignupUiState.Error -> signupState.message
        is SignupUiState.NotConfigured -> signupState.message
        else -> null
    }

    LaunchedEffect(signupState) {
        if (signupState !is SignupUiState.Error && signupState !is SignupUiState.NotConfigured) {
            localError = null
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!isConfigured) {
            Text(
                text = "Create a local account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Everything stays on this device. You can upgrade to cloud sign-in later if google-services.json is added.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = localDisplayName,
                onValueChange = { localDisplayName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Display name") },
                singleLine = true,
                enabled = !busy
            )
            OutlinedTextField(
                value = localEmail,
                onValueChange = { localEmail = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email (optional)") },
                supportingText = { Text("Used only to label your account in app; never sent anywhere.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !busy
            )
            Button(
                onClick = {
                    localError = null
                    if (localDisplayName.isBlank()) {
                        localError = "Enter a display name to continue."
                    } else {
                        onLocalSignUp(localDisplayName, localEmail.trim().ifEmpty { null })
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && localDisplayName.isNotBlank()
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create account")
                }
            }
            Text(
                text = "Cloud sign-in (phone / Google) is unavailable in this build.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "Continue with Phone",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhoneCountry.entries.forEach { option ->
                    FilterChip(
                        selected = country == option,
                        onClick = { country = option },
                        label = { Text(option.label) },
                        enabled = !busy
                    )
                }
            }
            if (country == PhoneCountry.Other) {
                OutlinedTextField(
                    value = otherCountryCode,
                    onValueChange = { otherCountryCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Country code") },
                    supportingText = { Text("Example: +44") },
                    singleLine = true,
                    enabled = !busy
                )
            }
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it.filter { ch -> ch.isDigit() || ch == ' ' } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mobile number") },
                supportingText = { Text("Enter digits only; country code comes from the picker above.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                enabled = !busy
            )
            Button(
                onClick = {
                    localError = null
                    val normalized = normalizePhoneE164(country, otherCountryCode, phoneNumber)
                    if (normalized == null) {
                        localError = "Enter a valid E.164 phone number with country code."
                    } else if (phoneUnavailableMessage != null) {
                        localError = phoneUnavailableMessage
                    } else {
                        onStartPhone(normalized)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Continue with Phone")
                }
            }

            if (awaitingCode != null) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("6-digit OTP") },
                    supportingText = { Text("Code sent to ${awaitingCode.phoneNumber}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !busy
                )
                Button(
                    onClick = {
                        if (code.length == 6) {
                            onConfirmCode(code)
                        } else {
                            localError = "Enter the 6-digit verification code."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && code.length == 6
                ) {
                    Text("Verify code")
                }
            }

            OutlinedButton(
                onClick = onGoogleSignIn,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && phoneUnavailableMessage == null
            ) {
                Text("Continue with Google")
            }
        }

        message?.let {
            Text(
                text = it.ifBlank { FIREBASE_NOT_CONFIGURED_MESSAGE },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onContinueAsGuest,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) {
            Text("Continue as guest")
        }
        onBack?.let {
            OutlinedButton(
                onClick = it,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                Text("Back")
            }
        }
    }
}

private enum class PhoneCountry(val label: String, val prefix: String?) {
    India("+91", "+91"),
    NorthAmerica("+1", "+1"),
    Other("Other", null)
}

private fun normalizePhoneE164(
    country: PhoneCountry,
    otherCountryCode: String,
    phoneNumber: String
): String? {
    val digits = phoneNumber.filter(Char::isDigit)
    if (digits.isBlank()) return null
    val prefix = when (country) {
        PhoneCountry.India -> "+91"
        PhoneCountry.NorthAmerica -> "+1"
        PhoneCountry.Other -> otherCountryCode.trim()
    }
    if (!prefix.startsWith('+')) return null
    val normalizedPrefix = "+" + prefix.drop(1).filter(Char::isDigit)
    if (normalizedPrefix.length <= 1) return null
    return normalizedPrefix + digits
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
