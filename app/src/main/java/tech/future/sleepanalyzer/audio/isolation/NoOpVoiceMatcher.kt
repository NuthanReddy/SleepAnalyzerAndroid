package tech.future.sleepanalyzer.audio.isolation

import tech.future.sleepanalyzer.audio.AttributionResult
import tech.future.sleepanalyzer.audio.Attribution
import tech.future.sleepanalyzer.audio.FeatureVector

/** Default matcher when isolation is disabled or no profile is enrolled. */
class NoOpVoiceMatcher : VoiceMatcher {
    override fun match(features: FeatureVector) = AttributionResult(Attribution.UNKNOWN, 0f)
}
