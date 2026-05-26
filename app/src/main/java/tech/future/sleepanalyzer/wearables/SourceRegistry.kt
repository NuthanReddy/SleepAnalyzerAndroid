package tech.future.sleepanalyzer.wearables

import android.content.Context

/**
 * Registry of all wearable [WearableSource] strategies known to the app.
 * Today there's only [HealthConnectSource], but the registry pattern makes it trivial to
 * add Samsung/Fitbit/Garmin/Wear OS later without touching call sites.
 *
 * Sources are constructed lazily and cached per process.
 */
object SourceRegistry {

    private val sources = mutableListOf<(Context) -> WearableSource>(
        { ctx -> HealthConnectSource(ctx) }
    )

    @Volatile
    private var cached: List<WearableSource>? = null

    fun all(context: Context): List<WearableSource> = cached ?: synchronized(this) {
        val list = sources.map { it(context.applicationContext) }
        cached = list
        list
    }

    suspend fun available(context: Context): List<WearableSource> =
        all(context).filter { runCatching { it.isAvailable() }.getOrDefault(false) }

    fun register(factory: (Context) -> WearableSource) {
        synchronized(this) {
            sources += factory
            cached = null
        }
    }
}
