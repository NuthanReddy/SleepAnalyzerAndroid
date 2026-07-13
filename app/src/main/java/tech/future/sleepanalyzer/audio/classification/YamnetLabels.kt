package tech.future.sleepanalyzer.audio.classification

import tech.future.sleepanalyzer.audio.AudioEventType

/**
 * Maps YAMNet / AudioSet class indices onto the app's [AudioEventType]s and decides the final
 * label for an event from the per-class scores YAMNet produced across the event's windows.
 *
 * Kept free of TensorFlow types so the mapping and decision rules can be unit-tested on the JVM
 * without the native runtime or the model.
 */
object YamnetLabels {

    /** AudioSet index of the "Silence" class. */
    const val SILENCE_INDEX = 494

    /**
     * Minimum score for a mapped class to win. YAMNet is confident on clear coughs/snores; a low
     * floor still keeps unrelated ambient classes (fans, room tone) out because those never map to
     * one of our target groups in the first place.
     */
    const val MIN_SCORE = 0.18f

    /** Treat the clip as silence only when YAMNet is quite sure of it. */
    const val SILENCE_SCORE = 0.6f

    /**
     * Group an AudioSet class index into one of our event types, or null if it is not a sound we
     * track. Indices come from yamnet_class_map.csv.
     */
    fun groupFor(index: Int): AudioEventType? = when (index) {
        38, 41 -> AudioEventType.SNORE                 // Snoring, Snort
        42, 43, 44 -> AudioEventType.COUGH             // Cough, Throat clearing, Sneeze
        0, 1, 2, 3, 5, 12, 65 -> AudioEventType.TALK   // Speech, Child speech, Conversation,
                                                       // Narration, Speech synthesizer, Whispering,
                                                       // speech babble
        else -> null
    }

    /**
     * Decide the event type from the best score seen per target group (aggregated across all
     * windows of the clip), the best silence score, and coarse loudness. Returns the type and a
     * confidence in 0..1.
     *
     * Order: a confident target class wins; otherwise a confident silence; otherwise loud audio is
     * NOISE and quiet audio is UNKNOWN — mirroring the heuristic classifier's tail so downstream
     * persistence rules behave identically.
     */
    fun decide(
        groupScores: Map<AudioEventType, Float>,
        silenceScore: Float,
        rms: Float,
        peak: Int
    ): Pair<AudioEventType, Float> {
        val winner = groupScores.entries.maxByOrNull { it.value }
        if (winner != null && winner.value >= MIN_SCORE) {
            return winner.key to winner.value.coerceIn(MIN_SCORE, 0.99f)
        }
        if (silenceScore >= SILENCE_SCORE) {
            return AudioEventType.SILENCE to silenceScore.coerceIn(0f, 1f)
        }
        if (rms > 500f || peak > 6000) {
            return AudioEventType.NOISE to (rms / 4000f).coerceIn(0.3f, 0.85f)
        }
        return AudioEventType.UNKNOWN to 0.3f
    }
}
