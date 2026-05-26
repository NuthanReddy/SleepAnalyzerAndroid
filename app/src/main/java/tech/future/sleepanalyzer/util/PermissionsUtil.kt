package tech.future.sleepanalyzer.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Centralized permission checks. UI screens use rememberPermissionState from accompanist for the request flow.
 */
object PermissionsUtil {

    /** Returns Android permission strings that the recorder feature needs at runtime. */
    fun recorderPermissions(): List<String> {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms
    }

    /** Tracker doesn't need RECORD_AUDIO unless recording is also on. */
    fun trackerPermissions(): List<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms
    }

    fun notificationPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null

    fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isRecordAudioGranted(context: Context) = isGranted(context, Manifest.permission.RECORD_AUDIO)

    fun canPostNotifications(context: Context): Boolean {
        val perm = notificationPermission() ?: return true
        return isGranted(context, perm)
    }

    /** On Android 12+ exact alarms require a separate user-granted capability. */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(android.app.AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    /** Intent that takes the user to the system "Alarms & reminders" screen for this app. */
    fun exactAlarmSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
