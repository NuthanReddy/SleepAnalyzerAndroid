package tech.future.sleepanalyzer.audio.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Unit tests for [SpectralNoiseReducer]. They exercise the two behaviours that matter for accuracy:
 * the filter is transparent when there is nothing to remove, and it strongly attenuates steady
 * background noise while leaving a loud event (a snore/talk/cough analogue - a strong tone) intact.
 */
class SpectralNoiseReducerTest {

    private val sampleRate = 22050
    private val frameLen = sampleRate / 30 // ~735 samples, matching AudioRecordSource

    private fun rms(samples: ShortArray, from: Int = 0, to: Int = samples.size): Float {
        if (to <= from) return 0f
        var sum = 0.0
        for (i in from until to) sum += samples[i].toDouble() * samples[i]
        return kotlin.math.sqrt(sum / (to - from)).toFloat()
    }

    /** Streams [total] samples produced by [gen] through the reducer in mic-sized frames. */
    private fun run(reducer: SpectralNoiseReducer, total: Int, gen: (Int) -> Short): ShortArray {
        val out = ShortArray(total)
        var written = 0
        var idx = 0
        while (idx < total) {
            val len = minOf(frameLen, total - idx)
            val frame = ShortArray(len) { gen(idx + it) }
            val cleaned = reducer.process(frame, len)
            System.arraycopy(cleaned, 0, out, written, cleaned.size)
            written += cleaned.size
            idx += len
        }
        return out
    }

    private fun tone(index: Int, freqHz: Float, amp: Float): Short {
        val v = amp * sin(2.0 * PI * freqHz * index / sampleRate)
        return v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    @Test
    fun `passthrough preserves a tone when nothing is subtracted`() {
        // overSubtraction = 0 => the spectral gain is always 1, so the reducer must reconstruct the
        // input (delayed) essentially bit-for-bit thanks to unity-gain overlap-add.
        val reducer = SpectralNoiseReducer(sampleRate, overSubtraction = 0f)
        val total = sampleRate * 2
        val out = run(reducer, total) { tone(it, 440f, 6000f) }

        val tail = total - sampleRate // last second, past latency/warm-up
        val inputTailRms = 6000f / kotlin.math.sqrt(2f)
        val outRms = rms(out, tail, total)
        val ratio = outRms / inputTailRms
        assertTrue("tone should pass through near unity, ratio=$ratio", ratio in 0.85f..1.15f)
    }

    @Test
    fun `stationary noise is strongly attenuated after the profile is learned`() {
        val reducer = SpectralNoiseReducer(sampleRate)
        val rng = Random(42)
        val amp = 1500
        val total = sampleRate * 2
        val out = run(reducer, total) { (rng.nextInt(-amp, amp + 1)).toShort() }

        val tail = total - sampleRate
        // Re-derive the input RMS of uniform noise analytically (deterministic amp bound).
        val inputRms = amp / kotlin.math.sqrt(3f)
        val outRms = rms(out, tail, total)
        assertTrue(
            "steady noise should be cut well below half, in=$inputRms out=$outRms",
            outRms < inputRms * 0.6f
        )
    }

    @Test
    fun `a loud event survives denoising amid background noise`() {
        val reducer = SpectralNoiseReducer(sampleRate)
        val rng = Random(7)
        val noiseAmp = 250
        val toneAmp = 5000f
        val learnSamples = sampleRate       // 1s of noise only to learn the profile
        val total = sampleRate * 2

        val out = run(reducer, total) { i ->
            val noise = rng.nextInt(-noiseAmp, noiseAmp + 1)
            if (i < learnSamples) {
                noise.toShort()
            } else {
                val t = toneAmp * sin(2.0 * PI * 600f * i / sampleRate)
                (t + noise).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        val eventRms = rms(out, learnSamples + frameLen * 2, total)
        val toneOnlyRms = toneAmp / kotlin.math.sqrt(2f)
        assertTrue(
            "the tone event should remain loud (>half its energy), out=$eventRms tone=$toneOnlyRms",
            eventRms > toneOnlyRms * 0.5f
        )
    }

    @Test
    fun `process returns exactly as many samples as it is given`() {
        val reducer = SpectralNoiseReducer(sampleRate)
        var totalIn = 0
        var totalOut = 0
        repeat(50) {
            val len = frameLen
            val frame = ShortArray(len) { tone(totalIn + it, 300f, 1000f) }
            val out = reducer.process(frame, len)
            assertEquals("each frame is length-preserving", len, out.size)
            totalIn += len
            totalOut += out.size
        }
        assertEquals(totalIn, totalOut)
    }

    @Test
    fun `empty input yields empty output`() {
        val reducer = SpectralNoiseReducer(sampleRate)
        assertEquals(0, reducer.process(ShortArray(0), 0).size)
    }

    @Test
    fun `tiny sub-hop frames still stream without losing samples`() {
        val reducer = SpectralNoiseReducer(sampleRate)
        val total = 4096
        var produced = 0
        var idx = 0
        while (idx < total) {
            val len = minOf(50, total - idx) // far smaller than the FFT hop
            val frame = ShortArray(len) { tone(idx + it, 500f, 2000f) }
            produced += reducer.process(frame, len).size
            idx += len
        }
        assertEquals("cumulative output tracks cumulative input", total, produced)
    }

    @Test
    fun `reset makes the instance reusable`() {
        val reducer = SpectralNoiseReducer(sampleRate, overSubtraction = 0f)
        run(reducer, sampleRate) { tone(it, 440f, 4000f) }
        reducer.reset()

        val total = sampleRate
        val out = run(reducer, total) { tone(it, 440f, 4000f) }
        val tail = total / 2
        val outRms = rms(out, tail, total)
        val inputTailRms = 4000f / kotlin.math.sqrt(2f)
        assertTrue("after reset passthrough still works, out=$outRms", outRms > inputTailRms * 0.7f)
    }

    @Test
    fun `rejects non power of two fft size`() {
        var threw = false
        try {
            SpectralNoiseReducer(sampleRate, fftSize = 1000)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("fftSize must be validated", threw)
    }
}
