package tech.future.sleepanalyzer.audio.classification

import android.content.Context

/**
 * Factory for the active classifier. Prefers the bundled YAMNet model (dedicated cough/snore/speech
 * classes); if the model can't be loaded on this device it transparently falls back to the
 * heuristic [SpectralClassifier] so recording still labels events.
 */
object ClassifierFactory {
    enum class Backend { YAMNET, SPECTRAL, AMPLITUDE }

    @Volatile private var preferred: Backend = Backend.YAMNET

    // The TFLite interpreter is expensive to create and immutable, so keep one resident and reuse
    // it across recording sessions. Guarded by [this] for first-load safety.
    @Volatile private var yamnet: YamnetClassifier? = null
    @Volatile private var yamnetTried = false

    fun setBackend(backend: Backend) { preferred = backend }

    /** Legacy entry point (no context): can only build feature-only classifiers. */
    fun create(): AudioClassifier = when (preferred) {
        Backend.AMPLITUDE -> AmplitudeClassifier()
        else -> SpectralClassifier()
    }

    fun create(context: Context): AudioClassifier = when (preferred) {
        Backend.AMPLITUDE -> AmplitudeClassifier()
        Backend.SPECTRAL -> SpectralClassifier()
        Backend.YAMNET -> yamnetOrFallback(context)
    }

    private fun yamnetOrFallback(context: Context): AudioClassifier {
        yamnet?.let { return it }
        synchronized(this) {
            yamnet?.let { return it }
            if (!yamnetTried) {
                yamnetTried = true
                yamnet = YamnetClassifier.create(context.applicationContext)
            }
        }
        return yamnet ?: SpectralClassifier()
    }
}
