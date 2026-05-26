@file:Suppress("unused")

package tech.future.sleepanalyzer.ui.onboarding

import com.google.accompanist.permissions.PermissionStatus

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
annotation class ExperimentalMaterial3Api

typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope

val PermissionStatus.isGranted: Boolean
    get() = this is PermissionStatus.Granted
