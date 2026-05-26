package tech.future.sleepanalyzer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SleepColorScheme = darkColorScheme(
    primary = SleepSecondary,
    onPrimary = SleepOnPrimary,
    primaryContainer = SleepPrimaryLight,
    onPrimaryContainer = SleepOnPrimary,
    secondary = SleepTertiary,
    onSecondary = SleepPrimary,
    secondaryContainer = SleepTertiaryDark,
    onSecondaryContainer = SleepOnPrimary,
    tertiary = SleepREM,
    onTertiary = SleepOnPrimary,
    background = SleepBackground,
    onBackground = SleepOnBackground,
    surface = SleepSurface,
    onSurface = SleepOnSurface,
    surfaceVariant = SleepSurfaceVariant,
    onSurfaceVariant = SleepOnSurfaceVariant,
)

@Composable
fun SleepAnalyzerTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SleepBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = SleepColorScheme,
        typography = Typography,
        content = content
    )
}
