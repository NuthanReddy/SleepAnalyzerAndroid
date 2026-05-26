package tech.future.sleepanalyzer.auth

import android.app.Activity
import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import tech.future.sleepanalyzer.util.awaitValue

class PhoneAuthCoordinator {
    sealed interface VerificationResult {
        data class CodeSent(
            val verificationId: String,
            val resendToken: PhoneAuthProvider.ForceResendingToken
        ) : VerificationResult

        data class AutoVerified(val credential: PhoneAuthCredential) : VerificationResult
        data class Failed(val message: String) : VerificationResult
    }

    suspend fun startVerification(activity: Activity, phoneE164: String): VerificationResult {
        val firebaseAuth = firebaseAuthOrNull() ?: return VerificationResult.Failed(FIREBASE_NOT_CONFIGURED_MESSAGE)
        firebaseAuth.setLanguageCode(Locale.getDefault().toLanguageTag())
        Log.d(TAG, "Starting phone verification for ${phoneE164.take(4)}***")
        return suspendCancellableCoroutine { continuation ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    if (continuation.isActive) {
                        continuation.resume(VerificationResult.AutoVerified(credential))
                    }
                }

                override fun onVerificationFailed(exception: com.google.firebase.FirebaseException) {
                    if (continuation.isActive) {
                        continuation.resume(
                            VerificationResult.Failed(
                                exception.localizedMessage ?: "Could not start phone verification."
                            )
                        )
                    }
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    if (continuation.isActive) {
                        continuation.resume(VerificationResult.CodeSent(verificationId, token))
                    }
                }
            }

            val options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setActivity(activity)
                .setPhoneNumber(phoneE164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    suspend fun verifyCode(verificationId: String, code: String): Result<FirebaseUser> {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        return signInWithCredential(credential)
    }

    suspend fun signInWithCredential(credential: PhoneAuthCredential): Result<FirebaseUser> {
        val firebaseAuth = firebaseAuthOrNull() ?: return Result.failure(firebaseNotConfiguredException())
        return runCatching {
            val authResult = firebaseAuth.signInWithCredential(credential).awaitValue()
            authResult.user ?: error("Phone sign-in completed without a Firebase user")
        }
    }

    companion object {
        private const val TAG = "PhoneAuthCoordinator"
    }
}
