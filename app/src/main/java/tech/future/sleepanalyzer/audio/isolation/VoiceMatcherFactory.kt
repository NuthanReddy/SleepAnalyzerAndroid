package tech.future.sleepanalyzer.audio.isolation

import tech.future.sleepanalyzer.data.db.entity.VoiceProfile
import tech.future.sleepanalyzer.util.Constants

/**
 * Returns the correct [VoiceMatcher] strategy based on whether isolation
 * is enabled and a profile has been enrolled.
 */
object VoiceMatcherFactory {
    fun create(profile: VoiceProfile?, isolationEnabled: Boolean): VoiceMatcher {
        if (!isolationEnabled || profile == null) return NoOpVoiceMatcher()
        return ProfileVoiceMatcher(profile, Constants.VOICE_MATCH_THRESHOLD)
    }
}
