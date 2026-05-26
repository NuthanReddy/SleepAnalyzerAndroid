# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room - keep entity field names (used by generated code)
-keep class tech.future.sleepanalyzer.data.db.entity.** { *; }

# Kotlin Coroutines (R8 already handles most, this prevents missing field warnings)
-dontwarn kotlinx.coroutines.debug.**

# Keep Compose generated lambdas - already handled by AGP but be safe
-keep class androidx.compose.runtime.** { *; }