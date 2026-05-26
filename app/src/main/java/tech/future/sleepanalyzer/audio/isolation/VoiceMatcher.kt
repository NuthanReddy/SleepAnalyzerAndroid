package tech.future.sleepanalyzer.audio.isolation

import tech.future.sleepanalyzer.audio.AttributionResult
import tech.future.sleepanalyzer.audio.FeatureVector

/**
 * Strategy interface for attributing an event to the enrolled user vs. an unknown speaker.
 * Implementations are pure functions over [FeatureVector] for thread safety.
 */
fun interface VoiceMatcher {
    fun match(features: FeatureVector): AttributionResult
}
