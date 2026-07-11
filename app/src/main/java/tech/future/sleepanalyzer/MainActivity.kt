package tech.future.sleepanalyzer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import tech.future.sleepanalyzer.di.ServiceLocator
import tech.future.sleepanalyzer.sync.CloudSyncScheduler
import tech.future.sleepanalyzer.ui.alarm.AlarmRingingScreen
import tech.future.sleepanalyzer.ui.alarm.AlarmScreen
import tech.future.sleepanalyzer.ui.auth.SignupScreen
import tech.future.sleepanalyzer.ui.games.AlertnessGameScreen
import tech.future.sleepanalyzer.ui.goals.SleepGoalScreen
import tech.future.sleepanalyzer.ui.more.MoreScreen
import tech.future.sleepanalyzer.ui.navigation.Screen
import tech.future.sleepanalyzer.ui.onboarding.OnboardingScreen
import tech.future.sleepanalyzer.ui.onboarding.VoiceEnrollScreen
import tech.future.sleepanalyzer.ui.profile.ProfileEditorScreen
import tech.future.sleepanalyzer.ui.programs.SleepProgramsScreen
import tech.future.sleepanalyzer.ui.recorder.RecorderScreen
import tech.future.sleepanalyzer.ui.sessions.SessionsScreen
import tech.future.sleepanalyzer.ui.settings.PrivacyScreen
import tech.future.sleepanalyzer.ui.settings.SettingsScreen
import tech.future.sleepanalyzer.ui.sounds.SoundPlayerScreen
import tech.future.sleepanalyzer.ui.sounds.SoundsLibraryScreen
import tech.future.sleepanalyzer.ui.stats.DetailedStatsScreen
import tech.future.sleepanalyzer.ui.theme.SleepAnalyzerTheme
import tech.future.sleepanalyzer.ui.tracker.SleepResultScreen
import tech.future.sleepanalyzer.ui.tracker.SleepTrackerScreen
import tech.future.sleepanalyzer.util.Constants
import tech.future.sleepanalyzer.wearables.WearableScheduler

class MainActivity : ComponentActivity() {

    private var initialDestination by mutableStateOf<String?>(null)
    private var initialAlarmId by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ServiceLocator + WearableScheduler are now initialized in SleepAnalyzerApp.onCreate
        // so the activity launch path stays defensive even if those subsystems failed earlier.
        val setupCompleted = runCatching {
            runBlocking { ServiceLocator.preferences.setupCompletedFlow.firstOrNull() ?: false }
        }.getOrDefault(false)
        updateInitialNavigation(intent)
        enableEdgeToEdge()
        setContent {
            SleepAnalyzerTheme {
                SleepAnalyzerApp(
                    startDestination = if (setupCompleted) Screen.Track.route else Screen.Onboarding.route,
                    initialDestination = initialDestination,
                    initialAlarmId = initialAlarmId,
                    onSignedIn = {
                        runCatching {
                            CloudSyncScheduler.runOnce(this@MainActivity)
                            CloudSyncScheduler.schedulePeriodic(this@MainActivity)
                        }
                    },
                    onSignedOut = {
                        runCatching { CloudSyncScheduler.cancel(this@MainActivity) }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateInitialNavigation(intent)
    }

    private fun updateInitialNavigation(intent: Intent?) {
        initialDestination = intent?.getStringExtra(Constants.EXTRA_NAVIGATE_TO)
        initialAlarmId = intent?.getLongExtra(Constants.EXTRA_ALARM_ID, -1L) ?: -1L
    }
}

@Composable
fun SleepAnalyzerApp(
    startDestination: String = Screen.Track.route,
    initialDestination: String? = null,
    initialAlarmId: Long = -1L,
    onSignedIn: () -> Unit = {},
    onSignedOut: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(initialDestination, initialAlarmId) {
        if (initialDestination == Constants.NAV_ALARM_RINGING) {
            navController.navigate(Screen.AlarmRinging.createRoute(initialAlarmId)) {
                launchSingleTop = true
            }
        }
    }

    val bottomBarRoutes = Screen.bottomNavItems.map { it.route }.toSet()
    val noBottomBarRoutes = setOf(
        Screen.Onboarding.route,
        Screen.VoiceEnroll.route,
        Screen.Settings.route,
        Screen.Profile.route,
        Screen.Signup.route
    )
    val showBottomBar = currentDestination?.route in bottomBarRoutes &&
        currentDestination?.route !in noBottomBarRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                screen.icon?.let {
                                    Icon(it, contentDescription = screen.title)
                                }
                            },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Track.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Screen.Track.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                    onSignedIn = onSignedIn
                )
            }

            composable(Screen.VoiceEnroll.route) {
                VoiceEnrollScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onReEnroll = { navController.navigate(Screen.VoiceEnroll.route) },
                    onOpenProfile = { navController.navigate(Screen.Profile.route) },
                    onOpenSignup = { navController.navigate(Screen.Signup.route) },
                    onOpenPrivacy = { navController.navigate(Screen.Privacy.route) },
                    onSignedOut = { goHome ->
                        onSignedOut()
                        if (goHome) {
                            navController.navigate(Screen.Track.route) {
                                popUpTo(Screen.Track.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileEditorScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Privacy.route) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Signup.route) {
                SignupScreen(
                    onBack = { navController.popBackStack() },
                    onSignedIn = {
                        onSignedIn()
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Track.route) {
                SleepTrackerScreen(
                    onNavigateToResult = { sessionId ->
                        navController.navigate(Screen.SleepResult.createRoute(sessionId))
                    },
                    onNavigateToSounds = { navController.navigate(Screen.Sounds.route) },
                    onNavigateToAlarm = { navController.navigate(Screen.Alarm.route) },
                    onNavigateToRecorder = { navController.navigate(Screen.Recorder.route) }
                )
            }

            composable(Screen.Sounds.route) {
                SoundsLibraryScreen(
                    onCategoryClick = { categoryId ->
                        navController.navigate(Screen.SoundPlayer.createRoute(categoryId))
                    }
                )
            }

            composable(Screen.Alarm.route) {
                AlarmScreen()
            }

            composable(Screen.More.route) {
                MoreScreen(
                    onNavigateToGoals = { navController.navigate(Screen.Goals.route) },
                    onNavigateToSounds = { navController.navigate(Screen.Sounds.route) },
                    onNavigateToAlarm = { navController.navigate(Screen.Alarm.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                    onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
                    onNavigateToGame = { navController.navigate(Screen.Game.route) },
                    onNavigateToRecorder = { navController.navigate(Screen.Recorder.route) }
                )
            }

            composable(
                route = Screen.SleepResult.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
                SleepResultScreen(
                    sessionId = sessionId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.SoundPlayer.route,
                arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getString("category") ?: ""
                SoundPlayerScreen(
                    categoryId = categoryId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Recorder.route) {
                RecorderScreen()
            }

            composable(Screen.Stats.route) {
                DetailedStatsScreen(onBack = null)
            }

            composable(Screen.Sessions.route) {
                SessionsScreen(
                    onOpenSession = { sessionId ->
                        navController.navigate(Screen.SleepResult.createRoute(sessionId))
                    }
                )
            }

            composable(Screen.Goals.route) {
                SleepGoalScreen()
            }

            composable(Screen.Programs.route) {
                SleepProgramsScreen()
            }

            composable(Screen.Game.route) {
                AlertnessGameScreen()
            }

            composable(
                route = Screen.AlarmRinging.route,
                arguments = listOf(
                    navArgument(Screen.AlarmRinging.ARG_ALARM_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val alarmId = backStackEntry.arguments?.getLong(Screen.AlarmRinging.ARG_ALARM_ID) ?: -1L
                AlarmRingingScreen(
                    alarmId = alarmId,
                    onDismiss = { navController.popBackStack() },
                    onSnooze = { navController.popBackStack() }
                )
            }
        }
    }
}
