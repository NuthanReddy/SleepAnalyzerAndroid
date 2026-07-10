package tech.future.sleepanalyzer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    // Bottom nav destinations
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Track : Screen("track", "Sleep", Icons.Default.Nightlight)
    data object Sounds : Screen("sounds", "Sounds", Icons.Default.MusicNote)
    data object Alarm : Screen("alarm", "Alarm", Icons.Default.Alarm)
    data object More : Screen("more", "Profile", Icons.Default.Person)

    // Onboarding
    data object Onboarding : Screen("onboarding", "Welcome")
    data object VoiceEnroll : Screen("voice_enroll", "Voice Setup")
    data object Settings : Screen("settings", "Settings")
    data object Privacy : Screen("privacy", "Privacy")
    data object Profile : Screen("profile", "Profile")
    data object Signup : Screen("signup", "Sign up")

    // Detail screens
    data object SleepResult : Screen("sleep_result/{sessionId}", "Sleep Result") {
        fun createRoute(sessionId: Long) = "sleep_result/$sessionId"
    }
    data object Recorder : Screen("recorder", "Recorder")
    data object Recordings : Screen("recordings", "Recordings")
    data object SoundPlayer : Screen("sound_player/{category}", "Sound Player") {
        fun createRoute(category: String) = "sound_player/$category"
    }
    data object Stats : Screen("stats", "Statistics", Icons.Default.BarChart)
    data object Notes : Screen("notes", "Journal", Icons.Default.GraphicEq)
    data object Goals : Screen("goals", "Sleep Goals")
    data object Programs : Screen("programs", "Programs", Icons.Default.SelfImprovement)
    data object MoreOptions : Screen("more_options", "More")
    data object Game : Screen("game", "Alertness Game")
    data object AlarmRinging : Screen("alarm_ringing?alarmId={alarmId}", "Alarm Ringing") {
        const val ARG_ALARM_ID = "alarmId"
        fun createRoute(alarmId: Long) = "alarm_ringing?alarmId=$alarmId"
    }

    companion object {
        val bottomNavItems = listOf(Track, Programs, Notes, Stats, More)
    }
}

