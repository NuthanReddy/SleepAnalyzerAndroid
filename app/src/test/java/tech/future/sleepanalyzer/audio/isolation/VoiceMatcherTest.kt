package tech.future.sleepanalyzer.audio.isolation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.future.sleepanalyzer.audio.Attribution
import tech.future.sleepanalyzer.audio.FeatureVector
import tech.future.sleepanalyzer.data.db.entity.VoiceProfile

class VoiceMatcherFactoryTest {

    @Test
    fun `returns noop when isolation disabled`() {
        val matcher = VoiceMatcherFactory.create(
            profile = VoiceProfile(label = "me"),
            isolationEnabled = false
        )
        assertTrue("expected NoOpVoiceMatcher, got ${matcher::class.simpleName}",
            matcher is NoOpVoiceMatcher)
    }

    @Test
    fun `returns noop when no profile available`() {
        val matcher = VoiceMatcherFactory.create(profile = null, isolationEnabled = true)
        assertTrue("expected NoOpVoiceMatcher, got ${matcher::class.simpleName}",
            matcher is NoOpVoiceMatcher)
    }

    @Test
    fun `returns profile matcher when enabled with profile`() {
        val profile = VoiceProfile(
            label = "me",
            bandEnergyMeans = "1|2|3|4|5|6|7|8|9|10|11|12|13"
        )
        val matcher = VoiceMatcherFactory.create(profile = profile, isolationEnabled = true)
        assertTrue("expected ProfileVoiceMatcher, got ${matcher::class.simpleName}",
            matcher is ProfileVoiceMatcher)
    }

    @Test
    fun `noop returns unknown attribution`() {
        val result = NoOpVoiceMatcher().match(FeatureVector())
        assertEquals(Attribution.UNKNOWN, result.attribution)
        assertEquals(0f, result.confidence, 0.001f)
    }
}

class ProfileVoiceMatcherTest {

    private fun makeProfile() = VoiceProfile(
        label = "me",
        pitchMeanHz = 150f,
        pitchStdHz = 25f,
        bandEnergyMeans = "1|2|3|4|5|6|7|8|9|10|11|12|13"
    )

    private fun featuresMatching() = FeatureVector(
        pitchHz = 150f,
        bandEnergies = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f)
    )

    private fun featuresMismatch() = FeatureVector(
        pitchHz = 80f,
        bandEnergies = floatArrayOf(13f, 12f, 11f, 10f, 9f, 8f, 7f, 6f, 5f, 4f, 3f, 2f, 1f)
    )

    @Test
    fun `matching features attribute to user`() {
        val matcher = ProfileVoiceMatcher(makeProfile(), threshold = 0.65f)
        val result = matcher.match(featuresMatching())
        assertEquals(Attribution.USER, result.attribution)
        assertTrue("expected high confidence, got ${result.confidence}", result.confidence >= 0.65f)
    }

    @Test
    fun `mismatch attributes to partner`() {
        val matcher = ProfileVoiceMatcher(makeProfile(), threshold = 0.65f)
        val result = matcher.match(featuresMismatch())
        assertEquals(Attribution.PARTNER, result.attribution)
        assertTrue("confidence ${result.confidence} should be < threshold", result.confidence < 0.65f)
    }

    @Test
    fun `empty band energies returns unknown`() {
        val profile = VoiceProfile(label = "me", bandEnergyMeans = "")
        val matcher = ProfileVoiceMatcher(profile)
        val result = matcher.match(FeatureVector())
        assertEquals(Attribution.UNKNOWN, result.attribution)
        assertEquals(0f, result.confidence, 0.001f)
    }
}
