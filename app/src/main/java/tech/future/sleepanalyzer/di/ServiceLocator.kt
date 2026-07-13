package tech.future.sleepanalyzer.di

import android.content.Context
import kotlinx.coroutines.flow.firstOrNull
import tech.future.sleepanalyzer.audio.classification.AudioClassifier
import tech.future.sleepanalyzer.audio.classification.ClassifierFactory
import tech.future.sleepanalyzer.audio.encoder.EncoderFactory
import tech.future.sleepanalyzer.audio.encoder.PcmEncoder
import tech.future.sleepanalyzer.audio.isolation.VoiceMatcher
import tech.future.sleepanalyzer.audio.isolation.VoiceMatcherFactory
import tech.future.sleepanalyzer.auth.AuthRepository
import tech.future.sleepanalyzer.data.db.AppDatabase
import tech.future.sleepanalyzer.data.prefs.AppPreferences
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.sync.DataRequestRepository
import tech.future.sleepanalyzer.sync.SyncRepository
import tech.future.sleepanalyzer.wearables.WearableSyncManager

/**
 * Lightweight service locator. We avoid a DI framework (Hilt/Koin) to keep the apk
 * smaller and startup faster - everything here is a lazy singleton or a factory call.
 *
 * Call [initialize] from MainActivity / Service onCreate with the app context.
 */
object ServiceLocator {
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) appContext = context.applicationContext
            }
        }
    }

    private fun requireContext(): Context = appContext
        ?: throw IllegalStateException("ServiceLocator.initialize() must be called first")

    val repository: SleepRepository by lazy { SleepRepository(requireContext()) }
    val preferences: AppPreferences by lazy { AppPreferences(requireContext()) }
    val database: AppDatabase by lazy { AppDatabase.getDatabase(requireContext()) }
    val authRepository: AuthRepository by lazy { AuthRepository(requireContext(), repository, preferences) }
    val syncRepository: SyncRepository by lazy { SyncRepository(requireContext(), repository, authRepository) }
    val dataRequestRepository: DataRequestRepository by lazy { DataRequestRepository(authRepository, preferences) }

    /** Per-call factory to allow user prefs to switch backends without restart. */
    fun classifier(): AudioClassifier = ClassifierFactory.create(requireContext())

    suspend fun voiceMatcher(): VoiceMatcher {
        val enabled = preferences.voiceIsolationEnabledFlow.firstOrNull() ?: false
        val profile = repository.getActiveVoiceProfile()
        return VoiceMatcherFactory.create(profile, enabled)
    }

    fun encoder(): PcmEncoder = EncoderFactory.create()

    /** Lazy singleton for biometric sync. Safe to call from any service. */
    val wearableSyncManagerInstance: WearableSyncManager by lazy {
        WearableSyncManager(requireContext(), repository)
    }
    fun wearableSyncManager(): WearableSyncManager = wearableSyncManagerInstance
}

