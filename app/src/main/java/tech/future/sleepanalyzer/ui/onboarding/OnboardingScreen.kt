package tech.future.sleepanalyzer.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import tech.future.sleepanalyzer.data.db.entity.UserProfile
import tech.future.sleepanalyzer.di.ServiceLocator
import tech.future.sleepanalyzer.ui.auth.SignupPanel
import tech.future.sleepanalyzer.ui.auth.SignupUiState
import tech.future.sleepanalyzer.ui.profile.AutofillState
import tech.future.sleepanalyzer.ui.profile.ProfileForm
import tech.future.sleepanalyzer.ui.wearables.WearablesSection
import tech.future.sleepanalyzer.util.PermissionsUtil
import tech.future.sleepanalyzer.wearables.WearableSource

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSignedIn: () -> Unit = {},
    viewModel: OnboardingViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val profileDraft by viewModel.profileDraft.collectAsStateWithLifecycle()
    val signupState by viewModel.signupState.collectAsStateWithLifecycle()
    val autofillResult by viewModel.autofillResult.collectAsStateWithLifecycle()
    val healthConnectAutofillAvailable by viewModel.healthConnectAutofillAvailable.collectAsStateWithLifecycle()
    val wearablePermissionsGranted by viewModel.wearablePermissionsGranted.collectAsStateWithLifecycle()
    val enrollmentRecording by viewModel.enrollmentRecording.collectAsStateWithLifecycle()
    val enrollmentProgress by viewModel.enrollmentProgress.collectAsStateWithLifecycle()
    val enrollmentResult by viewModel.enrollmentResult.collectAsStateWithLifecycle()
    val enrollmentError by viewModel.enrollmentError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionsState = rememberMultiplePermissionsState(PermissionsUtil.recorderPermissions())
    val notificationPermission = PermissionsUtil.notificationPermission()
    val microphoneGranted = permissionsState.permissions.firstOrNull {
        it.permission == Manifest.permission.RECORD_AUDIO
    }?.status?.isGranted == true
    val notificationsGranted = notificationPermission == null || permissionsState.permissions.firstOrNull {
        it.permission == notificationPermission
    }?.status?.isGranted == true

    LaunchedEffect(currentStep) {
        if (currentStep == OnboardingStep.Complete) {
            onComplete()
        }
    }

    LaunchedEffect(currentStep, enrollmentResult?.createdAt) {
        if (currentStep == OnboardingStep.VoiceEnroll && enrollmentResult != null) {
            viewModel.finishOnboardingWithVoiceIsolation()
        }
    }

    LaunchedEffect(signupState) {
        when (signupState) {
            is SignupUiState.SignedIn -> {
                onSignedIn()
                viewModel.clearSignupState()
                viewModel.nextStep(saveProfile = false)
            }
            SignupUiState.Guest -> {
                viewModel.clearSignupState()
                viewModel.nextStep(saveProfile = false)
            }
            else -> Unit
        }
    }

    LaunchedEffect(autofillResult) {
        when (val result = autofillResult) {
            is AutofillState.Success -> {
                val message = when (result.filled) {
                    setOf("height", "weight") -> "Filled height + weight from Health Connect"
                    setOf("height") -> "Filled height from Health Connect"
                    setOf("weight") -> "Filled weight from Health Connect"
                    else -> "Filled profile data from Health Connect"
                }
                snackbarHostState.showSnackbar(message)
                viewModel.clearAutofillResult()
            }
            is AutofillState.Failed -> {
                snackbarHostState.showSnackbar(result.message)
                viewModel.clearAutofillResult()
            }
            else -> Unit
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 4 } + fadeOut())
                },
                label = "onboarding"
            ) { step ->
                when (step) {
                    OnboardingStep.Welcome -> WelcomeStep(onContinue = { viewModel.nextStep() })
                    OnboardingStep.Signup -> SignupStep(
                        signupState = signupState,
                        isConfigured = ServiceLocator.authRepository.isConfigured(),
                        onBack = viewModel::previousStep,
                        onStartPhone = { phone -> activity?.let { viewModel.startPhoneVerification(it, phone) } },
                        onConfirmCode = viewModel::confirmPhoneCode,
                        onGoogleSignIn = { activity?.let(viewModel::signInWithGoogle) },
                        onLocalSignUp = viewModel::signUpLocal,
                        onContinueAsGuest = viewModel::continueAsGuest,
                        phoneUnavailableMessage = if (activity == null) {
                            "Sign-in requires an activity context."
                        } else null
                    )
                    OnboardingStep.Permissions -> PermissionsStep(
                        microphoneGranted = microphoneGranted,
                        notificationsGranted = notificationsGranted,
                        onGrant = { permissionsState.launchMultiplePermissionRequest() },
                        onContinue = { viewModel.nextStep() },
                        onBack = viewModel::previousStep
                    )
                    OnboardingStep.AboutYou -> AboutYouStep(
                        profile = profileDraft,
                        viewModel = viewModel,
                        healthConnectAvailable = healthConnectAutofillAvailable,
                        onBack = viewModel::previousStep,
                        onSkip = { viewModel.nextStep(saveProfile = false) },
                        onContinue = { viewModel.nextStep() }
                    )
                    OnboardingStep.Wearables -> WearablesStep(
                        viewModel = viewModel,
                        permissionsGranted = wearablePermissionsGranted,
                        onBack = viewModel::previousStep,
                        onContinue = { viewModel.nextStep() }
                    )
                    OnboardingStep.VoiceIsolation -> VoiceIsolationStep(
                        onBack = viewModel::previousStep,
                        onSetUp = { viewModel.nextStep() },
                        onSkip = viewModel::skipVoiceIsolation
                    )
                    OnboardingStep.VoiceEnroll -> VoiceEnrollmentStep(
                        recording = enrollmentRecording,
                        progress = enrollmentProgress,
                        resultAvailable = enrollmentResult != null,
                        error = enrollmentError,
                        hasMicrophonePermission = microphoneGranted,
                        onBack = {
                            viewModel.cancelEnrollment()
                            viewModel.previousStep()
                        },
                        onStart = viewModel::startEnrollment,
                        onCancel = viewModel::cancelEnrollment,
                        onRequestPermission = { permissionsState.launchMultiplePermissionRequest() }
                    )
                    OnboardingStep.Complete -> CompleteStep()
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    StepScaffold(stepNumber = 1) {
        Text(
            text = "Sleep Analyzer",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Track sleep sessions, recordings, alarms, and guided programs in one place.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WelcomeFeatureRow(
                    icon = Icons.Default.Mic,
                    title = "Sleep recording",
                    subtitle = "Capture snoring, coughing, and nighttime sounds."
                )
                WelcomeFeatureRow(
                    icon = Icons.Default.Notifications,
                    title = "Smart reminders",
                    subtitle = "Stay informed with tracking, alarms, and wake-up notifications."
                )
                WelcomeFeatureRow(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Voice isolation",
                    subtitle = "Optionally enroll your voice so the app can better separate your sounds."
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }
    }
}

@Composable
private fun SignupStep(
    signupState: SignupUiState,
    isConfigured: Boolean,
    onBack: () -> Unit,
    onStartPhone: (String) -> Unit,
    onConfirmCode: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onLocalSignUp: (String, String?) -> Unit,
    onContinueAsGuest: () -> Unit,
    phoneUnavailableMessage: String?
) {
    StepScaffold(stepNumber = 2) {
        SignupPanel(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            title = "Create an account or stay local",
            subtitle = if (isConfigured) {
                "Sign in only if you want opt-in cloud sync and data-rights requests. The app stays fully usable as a guest."
            } else {
                "Cloud sign-in isn't wired into this build, but you can still create a local account so your data stays attached to a stable identity on this device."
            },
            signupState = signupState,
            isConfigured = isConfigured,
            onStartPhone = onStartPhone,
            onConfirmCode = onConfirmCode,
            onGoogleSignIn = onGoogleSignIn,
            onLocalSignUp = onLocalSignUp,
            onContinueAsGuest = onContinueAsGuest,
            onBack = onBack,
            phoneUnavailableMessage = phoneUnavailableMessage
        )
    }
}

@Composable
private fun PermissionsStep(
    microphoneGranted: Boolean,
    notificationsGranted: Boolean,
    onGrant: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    StepScaffold(stepNumber = 3) {
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Microphone access is needed for sleep audio and voice enrollment. Notifications keep alarms and foreground services visible.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PermissionStatusRow(
                    icon = Icons.Default.Mic,
                    title = "Microphone",
                    granted = microphoneGranted
                )
                PermissionStatusRow(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    granted = notificationsGranted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "You can continue even if you want to decide later. The app will ask again when a feature needs access.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            FilledTonalButton(
                onClick = onContinue,
                modifier = Modifier.weight(1f)
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun AboutYouStep(
    profile: UserProfile,
    viewModel: OnboardingViewModel,
    healthConnectAvailable: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit
) {
    StepScaffold(stepNumber = 4) {
        Text(
            text = "About you",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Share a few optional details so Sleep Analyzer can personalize insights and sleep-stage baselines.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileForm(
                profile = profile,
                onDisplayNameChange = viewModel::setDisplayName,
                onDateOfBirthChange = viewModel::setDateOfBirth,
                onBiologicalSexChange = viewModel::setBiologicalSex,
                onHeightCmChange = viewModel::setHeightCm,
                onWeightKgChange = viewModel::setWeightKg,
                onActivityLevelChange = viewModel::setActivityLevel,
                onUnitsChange = viewModel::setUnits,
                onSleepConditionsChange = viewModel::setSleepConditions,
                onMedicationsChange = viewModel::setMedications,
                onTypicalCaffeineCutoffHourChange = viewModel::setTypicalCaffeineCutoffHour,
                onShiftWorkScheduleChange = viewModel::setShiftWorkSchedule,
                compact = true,
                bodyHeader = {
                    OutlinedButton(
                        onClick = viewModel::autofillFromHealthConnect,
                        enabled = healthConnectAvailable,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Autofill from Health Connect")
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            FilledTonalButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("Skip")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun WearablesStep(
    viewModel: OnboardingViewModel,
    permissionsGranted: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val availableSources by produceState(initialValue = emptyList<WearableSource>(), key1 = viewModel) {
        value = viewModel.availableSources()
    }

    StepScaffold(stepNumber = 5) {
        Text(
            text = "Wearables",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (availableSources.isEmpty()) {
                "Health Connect can be installed later if you want heart rate and HRV data in your sleep reports."
            } else {
                "Connect ${availableSources.joinToString { it.displayName }} to import heart rate and other optional sleep signals."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WearablesSection(
                showHeader = false,
                onPermissionResult = viewModel::markWearablePermissionsGranted
            )
            if (permissionsGranted) {
                Text(
                    text = "Wearable access granted — you can sync now or continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            FilledTonalButton(
                onClick = onContinue,
                modifier = Modifier.weight(1f)
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun VoiceIsolationStep(
    onBack: () -> Unit,
    onSetUp: () -> Unit,
    onSkip: () -> Unit
) {
    StepScaffold(stepNumber = 6) {
        Text(
            text = "Voice isolation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Voice isolation helps tell your snores from a partner's. It needs a quick 5-second voice sample that stays on your device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Recommended if you share a room or want more accurate voice attribution.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "You can re-enroll or turn this off anytime in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onSetUp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Set up voice isolation")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            FilledTonalButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("Skip for now")
            }
        }
    }
}

@Composable
private fun VoiceEnrollmentStep(
    recording: Boolean,
    progress: Float,
    resultAvailable: Boolean,
    error: String?,
    hasMicrophonePermission: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onRequestPermission: () -> Unit
) {
    VoiceEnrollmentContent(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        title = "Speak naturally for 5 seconds",
        subtitle = "Use a normal speaking voice so Sleep Analyzer can learn your profile.",
        recording = recording,
        progress = progress,
        resultAvailable = resultAvailable,
        error = error,
        hasMicrophonePermission = hasMicrophonePermission,
        onStart = onStart,
        onCancel = onCancel,
        onRequestPermission = onRequestPermission,
        footer = {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back")
            }
        }
    )
}

@Composable
private fun CompleteStep() {
    StepScaffold(stepNumber = 7) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Finishing setup…",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Preparing your home screen.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StepScaffold(
    stepNumber: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Step $stepNumber of 7",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { stepNumber / 7f },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun WelcomeFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    granted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
        if (granted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "$title granted",
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = "Pending",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
