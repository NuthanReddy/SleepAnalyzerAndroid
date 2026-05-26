package tech.future.sleepanalyzer.auth

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

const val FIREBASE_NOT_CONFIGURED_MESSAGE =
    "Sign-in unavailable in this build. Add google-services.json to enable signup."

internal fun firebaseAppOrNull(): FirebaseApp? = try {
    FirebaseApp.getInstance()
} catch (_: IllegalStateException) {
    null
} catch (_: ApiException) {
    null
}

internal fun isPlaceholderFirebase(app: FirebaseApp): Boolean =
    app.options.apiKey.startsWith("AIzaSyPlaceholder")

internal fun firebaseAuthOrNull(): FirebaseAuth? {
    val app = firebaseAppOrNull() ?: return null
    if (isPlaceholderFirebase(app)) return null
    return runCatching { FirebaseAuth.getInstance(app) }.getOrNull()
}

fun firebaseNotConfiguredException(
    message: String = FIREBASE_NOT_CONFIGURED_MESSAGE
): IllegalStateException = IllegalStateException(message)

fun resolveDefaultWebClientId(context: Context): String {
    val resourceId = context.resources.getIdentifier(
        "default_web_client_id",
        "string",
        context.packageName
    )
    if (resourceId == 0) throw firebaseNotConfiguredException()
    return context.getString(resourceId).trim().takeIf { it.isNotEmpty() }
        ?: throw firebaseNotConfiguredException()
}
