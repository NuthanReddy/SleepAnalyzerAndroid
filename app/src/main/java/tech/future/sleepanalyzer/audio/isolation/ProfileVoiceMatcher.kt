package tech.future.sleepanalyzer.audio.isolation

import tech.future.sleepanalyzer.audio.AttributionResult
import tech.future.sleepanalyzer.audio.Attribution
import tech.future.sleepanalyzer.audio.FeatureVector
import tech.future.sleepanalyzer.audio.util.AudioMath
import tech.future.sleepanalyzer.data.db.entity.VoiceProfile
import kotlin.math.abs
import kotlin.math.exp

/**
 * Matches an incoming event against an enrolled voice profile by combining:
 * - cosine similarity of mel band energies (timbre)
 * - distance between event pitch and the profile pitch mean (in std-units)
 *
 * Confidence is in [0,1]; events above [threshold] are attributed to USER,
 * everything else stays PARTNER/UNKNOWN based on confidence sign.
 */
class ProfileVoiceMatcher(
    private val profile: VoiceProfile,
    private val threshold: Float = 0.65f
) : VoiceMatcher {

    private val profileBands: FloatArray = profile.bandEnergyMeans
        .split('|')
        .mapNotNull { it.toFloatOrNull() }
        .toFloatArray()

    override fun match(features: FeatureVector): AttributionResult {
        if (profileBands.isEmpty()) {
            return AttributionResult(Attribution.UNKNOWN, 0f)
        }
        val cos = AudioMath.cosineSimilarity(profileBands, features.bandEnergies)
            .coerceIn(-1f, 1f)
        // Normalize cos similarity into [0,1]
        val timbreScore = (cos + 1f) / 2f

        val pitchScore = if (features.pitchHz > 0f && profile.pitchStdHz > 1f) {
            val diff = abs(features.pitchHz - profile.pitchMeanHz)
            // Soft gaussian-like falloff around mean
            exp(-(diff * diff) / (2f * profile.pitchStdHz * profile.pitchStdHz)).toFloat()
        } else 0.5f

        val confidence = (timbreScore * 0.65f + pitchScore * 0.35f).coerceIn(0f, 1f)
        val attribution = if (confidence >= threshold) Attribution.USER else Attribution.PARTNER
        return AttributionResult(attribution, confidence)
    }
}
