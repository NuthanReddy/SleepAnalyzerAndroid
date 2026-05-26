package tech.future.sleepanalyzer.audio

/**
 * Categorizes a detected acoustic event during a sleep session.
 */
enum class AudioEventType(val key: String) {
    SNORE("snore"),
    COUGH("cough"),
    TALK("talk"),
    NOISE("noise"),
    SILENCE("silence"),
    UNKNOWN("unknown");

    companion object {
        fun fromKey(key: String?): AudioEventType =
            entries.firstOrNull { it.key == key } ?: UNKNOWN
    }
}

/** Who the event was attributed to when voice isolation is enabled. */
enum class Attribution(val key: String) {
    USER("user"),
    PARTNER("partner"),
    UNKNOWN("unknown");

    companion object {
        fun fromKey(key: String?): Attribution =
            entries.firstOrNull { it.key == key } ?: UNKNOWN
    }
}
