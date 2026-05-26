package tech.future.sleepanalyzer.audio.classification

/**
 * Factory for the active classifier. Today it always returns the spectral
 * implementation; once a TFLite model is bundled, the factory can read user
 * prefs / device capabilities and pick MlClassifier without touching callers.
 */
object ClassifierFactory {
    enum class Backend { SPECTRAL, AMPLITUDE }

    @Volatile private var preferred: Backend = Backend.SPECTRAL

    fun setBackend(backend: Backend) { preferred = backend }

    fun create(): AudioClassifier = when (preferred) {
        Backend.SPECTRAL -> SpectralClassifier()
        Backend.AMPLITUDE -> AmplitudeClassifier()
    }
}
