package tech.future.sleepanalyzer.audio.classification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.future.sleepanalyzer.audio.AudioEventType
import tech.future.sleepanalyzer.audio.FeatureVector

class SpectralClassifierTest {

    private val classifier = SpectralClassifier()

    private fun features(
        rms: Float = 300f,
        peak: Int = 2000,
        zcr: Float = 0.05f,
        centroid: Float = 1200f,
        flatness: Float = 0.3f,
        pitch: Float = 0f,
        periodicity: Float = 0f
    ) = FeatureVector(
        rms = rms,
        peak = peak,
        zeroCrossingRate = zcr,
        spectralCentroid = centroid,
        spectralRolloff = 0f,
        spectralFlatness = flatness,
        pitchHz = pitch,
        periodicity = periodicity,
        bandEnergies = FloatArray(13)
    )

    @Test
    fun `silence detected when peak and rms are tiny`() {
        val result = classifier.classify(features(rms = 100f, peak = 500))
        assertEquals(AudioEventType.SILENCE, result.type)
    }

    @Test
    fun `cough detected from short broadband burst`() {
        val result = classifier.classify(features(
            rms = 4000f, peak = 22000, flatness = 0.45f, periodicity = 0.2f, zcr = 0.08f
        ))
        assertEquals(AudioEventType.COUGH, result.type)
    }

    @Test
    fun `snore detected from rhythmic low pitch`() {
        val result = classifier.classify(features(
            rms = 1500f, peak = 9000, flatness = 0.25f, pitch = 120f, periodicity = 0.7f, centroid = 900f
        ))
        assertEquals(AudioEventType.SNORE, result.type)
    }

    @Test
    fun `talk detected from voice band pitch and centroid`() {
        val result = classifier.classify(features(
            rms = 1200f, peak = 8000, flatness = 0.40f, pitch = 200f, periodicity = 0.5f, centroid = 2200f
        ))
        assertEquals(AudioEventType.TALK, result.type)
    }

    @Test
    fun `noise fallback for loud unclassified`() {
        val result = classifier.classify(features(
            rms = 1200f, peak = 9000, flatness = 0.8f, pitch = 0f, periodicity = 0.1f
        ))
        assertEquals(AudioEventType.NOISE, result.type)
    }

    @Test
    fun `unknown returned for quiet ambiguous input`() {
        val result = classifier.classify(features(rms = 300f, peak = 2500, flatness = 0.5f))
        assertEquals(AudioEventType.UNKNOWN, result.type)
    }

    @Test
    fun `confidence in valid range for every classification`() {
        val cases = listOf(
            features(rms = 100f, peak = 500),
            features(rms = 4000f, peak = 22000, flatness = 0.45f, periodicity = 0.2f, zcr = 0.08f),
            features(rms = 1500f, peak = 9000, flatness = 0.25f, pitch = 120f, periodicity = 0.7f, centroid = 900f),
            features(rms = 1200f, peak = 8000, flatness = 0.40f, pitch = 200f, periodicity = 0.5f, centroid = 2200f)
        )
        cases.forEach {
            val out = classifier.classify(it)
            assertTrue("confidence out of range: ${out.confidence}", out.confidence in 0f..1f)
        }
    }
}
