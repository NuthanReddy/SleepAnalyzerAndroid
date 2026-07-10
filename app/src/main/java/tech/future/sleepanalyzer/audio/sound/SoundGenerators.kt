package tech.future.sleepanalyzer.audio.sound

import tech.future.sleepanalyzer.sounds.SoundLibrary
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class WhiteNoiseGenerator(
    amplitude: Int = 12000,
    private val rng: Random = Random.Default
) : SoundGenerator {
    private val maxAmplitude = amplitude.coerceIn(1, 12000)

    override fun next(out: ShortArray, bufferSize: Int) {
        for (i in 0 until bufferSize) {
            out[i] = rng.nextInt(-maxAmplitude, maxAmplitude).toShort()
        }
    }
}

/** Voss-McCartney pink noise generator (~1/f). */
class PinkNoiseGenerator(
    amplitude: Float = 9000f,
    private val rng: Random = Random.Default
) : SoundGenerator {
    private val rows = FloatArray(16)
    private var index = 0
    private val gain = amplitude.coerceIn(1000f, 12000f)

    override fun next(out: ShortArray, bufferSize: Int) {
        for (i in 0 until bufferSize) {
            index = (index + 1) and 0xFFFF
            var sum = 0f
            for (r in rows.indices) {
                if ((index ushr r) and 1 == 1) {
                    rows[r] = (rng.nextFloat() * 2f) - 1f
                }
                sum += rows[r]
            }
            out[i] = ((sum / rows.size) * gain).toInt().coerceIn(-12000, 12000).toShort()
        }
    }
}

/** Brown noise: integrated white noise (warmer). */
class BrownNoiseGenerator(
    private val stepScale: Float = 0.04f,
    private val damping: Float = 0.996f,
    amplitude: Float = 10000f,
    private val rng: Random = Random.Default
) : SoundGenerator {
    private val gain = amplitude.coerceIn(1000f, 12000f)
    private var last = 0f

    override fun next(out: ShortArray, bufferSize: Int) {
        for (i in 0 until bufferSize) {
            val white = ((rng.nextFloat() * 2f) - 1f) * stepScale
            last = (last + white).coerceIn(-1f, 1f) * damping
            out[i] = (last * gain).toInt().coerceIn(-12000, 12000).toShort()
        }
    }
}

/** Rain-like: bright pink noise with sparse droplets. */
class RainGenerator(
    private val baseGain: Float = 0.9f,
    private val impulseChance: Float = 0.0008f,
    private val impulseMin: Int = -9000,
    private val impulseMax: Int = 9000,
    private val rng: Random = Random.Default
) : SoundGenerator {
    private val pink = PinkNoiseGenerator(amplitude = 8500f, rng = rng)
    private var previous = 0f
    private var local = ShortArray(0)

    override fun next(out: ShortArray, bufferSize: Int) {
        if (local.size < bufferSize) local = ShortArray(bufferSize)
        pink.next(local, bufferSize)
        for (i in 0 until bufferSize) {
            val base = local[i].toFloat() * baseGain
            val bright = base - (previous * 0.85f)
            previous = base
            var sample = bright.toInt()
            if (rng.nextFloat() < impulseChance) {
                sample += rng.nextInt(impulseMin, impulseMax + 1)
            }
            out[i] = sample.coerceIn(-12000, 12000).toShort()
        }
    }
}

/** Ocean wave: slow amplitude envelope over brown noise. */
class OceanWaveGenerator(
    private val sampleRate: Int,
    private val periodSec: Double = 7.0
) : SoundGenerator {
    private val brown = BrownNoiseGenerator(stepScale = 0.03f, damping = 0.997f, amplitude = 9500f)
    private var phase = 0.0
    private var local = ShortArray(0)

    override fun next(out: ShortArray, bufferSize: Int) {
        if (local.size < bufferSize) local = ShortArray(bufferSize)
        brown.next(local, bufferSize)
        val step = (2 * PI) / (sampleRate * periodSec.coerceAtLeast(1.0))
        for (i in 0 until bufferSize) {
            val env = 0.25f + (0.75f * (0.5f + 0.5f * sin(phase).toFloat()))
            phase += step
            if (phase >= 2 * PI) phase -= 2 * PI
            out[i] = (local[i].toFloat() * env).toInt().coerceIn(-12000, 12000).toShort()
        }
    }
}

/**
 * Tone-shaping wrapper around any inner generator. Applies a one-pole low-pass and/or
 * a one-pole high-pass so we can carve distinctly different timbres (warm fan, bright
 * shower, narrow bandpass for crickets, etc.) without writing custom DSP per sound.
 */
class FilteredGenerator(
    private val inner: SoundGenerator,
    sampleRate: Int,
    lowPassCutoffHz: Float? = null,
    highPassCutoffHz: Float? = null
) : SoundGenerator {
    private val lpCoef: Float = lowPassCutoffHz?.let { coefFor(it, sampleRate) } ?: 1f
    private val hpCoef: Float = highPassCutoffHz?.let { coefFor(it, sampleRate) } ?: 0f
    private var lpState = 0f
    private var hpInputPrev = 0f
    private var hpOutputPrev = 0f
    private var scratch: ShortArray = ShortArray(0)

    override fun next(out: ShortArray, bufferSize: Int) {
        if (scratch.size < bufferSize) scratch = ShortArray(bufferSize)
        inner.next(scratch, bufferSize)
        for (i in 0 until bufferSize) {
            var sample = scratch[i].toFloat()
            // Low-pass
            lpState = lpState + lpCoef * (sample - lpState)
            sample = lpState
            // High-pass (one-pole differentiator with leaky integrator)
            if (hpCoef > 0f) {
                val y = hpCoef * (hpOutputPrev + sample - hpInputPrev)
                hpInputPrev = sample
                hpOutputPrev = y
                sample = y
            }
            out[i] = sample.toInt().coerceIn(-12000, 12000).toShort()
        }
    }

    private companion object {
        fun coefFor(cutoffHz: Float, sampleRate: Int): Float {
            // RC = 1 / (2 pi fc); coef = dt / (RC + dt)
            val dt = 1f / sampleRate
            val rc = 1f / (2f * PI.toFloat() * cutoffHz.coerceAtLeast(1f))
            return dt / (rc + dt)
        }
    }
}

/** Adds a sinusoidal hum at [hum_hz] on top of an inner generator (used for AC hum, fan, lullabies). */
class HumGenerator(
    private val inner: SoundGenerator?,
    private val sampleRate: Int,
    private val humHz: Float = 60f,
    private val humAmplitude: Float = 1500f,
    private val innerGain: Float = 0.6f
) : SoundGenerator {
    private var phase = 0.0
    private var scratch: ShortArray = ShortArray(0)

    override fun next(out: ShortArray, bufferSize: Int) {
        val step = (2 * PI) * humHz / sampleRate
        if (inner != null) {
            if (scratch.size < bufferSize) scratch = ShortArray(bufferSize)
            inner.next(scratch, bufferSize)
        }
        for (i in 0 until bufferSize) {
            val hum = sin(phase).toFloat() * humAmplitude
            phase += step
            if (phase >= 2 * PI) phase -= 2 * PI
            val mixed = if (inner != null) hum + (scratch[i].toFloat() * innerGain) else hum
            out[i] = mixed.toInt().coerceIn(-12000, 12000).toShort()
        }
    }
}

/**
 * Sparse short sinusoidal chirps at random pitches in the bird-song band.
 * Produces convincingly distinguishable "morning birds" at low CPU cost.
 */
class BirdChirpGenerator(
    private val sampleRate: Int,
    private val ambient: SoundGenerator = PinkNoiseGenerator(amplitude = 3500f),
    private val chirpPerSecond: Float = 0.9f,
    private val freqMin: Float = 1800f,
    private val freqMax: Float = 5200f
) : SoundGenerator {
    private val rng = Random.Default
    private var phase = 0.0
    private var freq = 3000f
    private var samplesRemaining = 0
    private var amplitude = 0f
    private var attack = 0f
    private var release = 0f
    private var sample = 0
    private var scratch: ShortArray = ShortArray(0)

    override fun next(out: ShortArray, bufferSize: Int) {
        if (scratch.size < bufferSize) scratch = ShortArray(bufferSize)
        ambient.next(scratch, bufferSize)
        val triggerProbability = chirpPerSecond / sampleRate
        for (i in 0 until bufferSize) {
            if (samplesRemaining <= 0 && rng.nextFloat() < triggerProbability) {
                freq = freqMin + rng.nextFloat() * (freqMax - freqMin)
                samplesRemaining = (sampleRate * (0.06 + rng.nextDouble() * 0.16)).toInt()
                amplitude = 5500f
                attack = amplitude / (samplesRemaining * 0.2f).coerceAtLeast(1f)
                release = amplitude / (samplesRemaining * 0.8f).coerceAtLeast(1f)
                sample = 0
                phase = 0.0
            }
            var value = scratch[i].toFloat()
            if (samplesRemaining > 0) {
                val env = if (sample < (samplesRemaining * 0.2f).toInt()) {
                    sample * attack
                } else {
                    amplitude - (sample - (samplesRemaining * 0.2f).toInt()) * release
                }.coerceAtLeast(0f)
                value += sin(phase).toFloat() * env
                phase += (2 * PI) * freq / sampleRate
                if (phase >= 2 * PI) phase -= 2 * PI
                sample++
                samplesRemaining--
            }
            out[i] = value.toInt().coerceIn(-12000, 12000).toShort()
        }
    }
}

/** Periodic narrowband bursts ~4 kHz to evoke cricket chirps. */
class CricketGenerator(
    private val sampleRate: Int,
    private val rate: Float = 3.0f,
    private val freq: Float = 4300f,
    private val ambient: SoundGenerator = BrownNoiseGenerator(stepScale = 0.012f, amplitude = 2500f)
) : SoundGenerator {
    private val period = (sampleRate / rate).toInt().coerceAtLeast(8000)
    private val burstLen = (sampleRate * 0.035).toInt().coerceAtLeast(64)
    private var counter = 0
    private var phase = 0.0
    private var scratch: ShortArray = ShortArray(0)

    override fun next(out: ShortArray, bufferSize: Int) {
        if (scratch.size < bufferSize) scratch = ShortArray(bufferSize)
        ambient.next(scratch, bufferSize)
        for (i in 0 until bufferSize) {
            var value = scratch[i].toFloat()
            if (counter < burstLen) {
                val t = counter.toFloat() / burstLen
                val env = (sin(PI * t).toFloat()) * 4500f
                value += sin(phase).toFloat() * env
                phase += (2 * PI) * freq / sampleRate
                if (phase >= 2 * PI) phase -= 2 * PI
            }
            counter++
            if (counter >= period) counter = 0
            out[i] = value.toInt().coerceIn(-12000, 12000).toShort()
        }
    }
}

/** Sparse exponentially-decaying impulses for crackling fire / forest twig sounds. */
class CrackleGenerator(
    private val sampleRate: Int,
    private val cracklesPerSecond: Float = 4f,
    private val ambient: SoundGenerator = BrownNoiseGenerator(stepScale = 0.02f, amplitude = 3500f)
) : SoundGenerator {
    private val rng = Random.Default
    private var decay = 0f
    private var phase = 0.0
    private var freq = 800f
    private var scratch: ShortArray = ShortArray(0)
    private val triggerProb = cracklesPerSecond / sampleRate

    override fun next(out: ShortArray, bufferSize: Int) {
        if (scratch.size < bufferSize) scratch = ShortArray(bufferSize)
        ambient.next(scratch, bufferSize)
        for (i in 0 until bufferSize) {
            if (rng.nextFloat() < triggerProb) {
                decay = 4500f * (0.4f + rng.nextFloat() * 0.6f)
                freq = 400f + rng.nextFloat() * 1800f
                phase = 0.0
            }
            var value = scratch[i].toFloat()
            if (decay > 1f) {
                value += sin(phase).toFloat() * decay
                phase += (2 * PI) * freq / sampleRate
                if (phase >= 2 * PI) phase -= 2 * PI
                decay *= 0.9985f
            }
            out[i] = value.toInt().coerceIn(-12000, 12000).toShort()
        }
    }
}

/** Slow major-triad lullaby with breath-rate amplitude envelope, used for sleep music & guided meditations. */
class TonalLullabyGenerator(
    private val sampleRate: Int,
    rootHz: Float = 130.81f, // C3
    private val breathRateHz: Float = 0.12f,
    private val amplitude: Float = 4000f
) : SoundGenerator {
    private val frequencies = floatArrayOf(rootHz, rootHz * 1.25f, rootHz * 1.5f, rootHz * 2f)
    private val phases = DoubleArray(frequencies.size)
    private var envPhase = 0.0

    override fun next(out: ShortArray, bufferSize: Int) {
        val envStep = (2 * PI) * breathRateHz / sampleRate
        val gains = floatArrayOf(0.45f, 0.30f, 0.25f, 0.18f)
        for (i in 0 until bufferSize) {
            val env = 0.35f + 0.65f * (0.5f + 0.5f * sin(envPhase).toFloat())
            envPhase += envStep
            if (envPhase >= 2 * PI) envPhase -= 2 * PI
            var sample = 0f
            for (h in frequencies.indices) {
                sample += sin(phases[h]).toFloat() * gains[h]
                phases[h] += (2 * PI) * frequencies[h] / sampleRate
                if (phases[h] >= 2 * PI) phases[h] -= 2 * PI
            }
            out[i] = (sample * amplitude * env).toInt().coerceIn(-12000, 12000).toShort()
        }
    }
}

/**
 * Soft narrated-story stand-in for "sleep stories": a themed [ambient] soundscape blended with a
 * gentle melodic [lullaby] bed so each story feels like a cohesive scene rather than a bare loop.
 * Defaults to a faint rain bed + low hummed lullaby (the original "stargazing" mix).
 */
class StoryAmbienceGenerator(
    sampleRate: Int,
    private val ambient: SoundGenerator = RainGenerator(baseGain = 0.5f, impulseChance = 0.0004f),
    private val lullaby: SoundGenerator = TonalLullabyGenerator(
        sampleRate, rootHz = 110f, breathRateHz = 0.08f, amplitude = 2400f
    ),
    private val ambientGain: Float = 0.45f,
    private val lullabyGain: Float = 1.0f
) : SoundGenerator {
    private var scratchA: ShortArray = ShortArray(0)
    private var scratchB: ShortArray = ShortArray(0)

    override fun next(out: ShortArray, bufferSize: Int) {
        if (scratchA.size < bufferSize) scratchA = ShortArray(bufferSize)
        if (scratchB.size < bufferSize) scratchB = ShortArray(bufferSize)
        ambient.next(scratchA, bufferSize)
        lullaby.next(scratchB, bufferSize)
        for (i in 0 until bufferSize) {
            val mixed = scratchA[i].toFloat() * ambientGain + scratchB[i].toFloat() * lullabyGain
            out[i] = mixed.toInt().coerceIn(-12000, 12000).toShort()
        }
    }
}

object SoundGeneratorFactory {
    fun create(soundId: String, sampleRate: Int): SoundGenerator {
        val categoryId = SoundLibrary.getSoundById(soundId)?.first?.id
        return when (soundId) {
            // ---- White noise family ----
            // Classic is a gently low-passed white noise: keeps the bright "white" character but
            // rolls off the harsh top end so it's smoother and less fatiguing over a full night.
            "white_noise_classic" -> FilteredGenerator(
                WhiteNoiseGenerator(amplitude = 11000),
                sampleRate, lowPassCutoffHz = 7200f
            )
            "white_noise_soft" -> FilteredGenerator(
                WhiteNoiseGenerator(amplitude = 9500),
                sampleRate, lowPassCutoffHz = 3500f
            )
            "fan_noise" -> FilteredGenerator(
                WhiteNoiseGenerator(amplitude = 8500),
                sampleRate, lowPassCutoffHz = 1800f, highPassCutoffHz = 120f
            )
            "ac_hum" -> HumGenerator(
                inner = FilteredGenerator(
                    BrownNoiseGenerator(stepScale = 0.015f, amplitude = 6500f),
                    sampleRate, lowPassCutoffHz = 600f
                ),
                sampleRate = sampleRate, humHz = 60f, humAmplitude = 1100f
            )

            // ---- Pink noise family ----
            "pink_noise_classic" -> PinkNoiseGenerator()
            "pink_noise_deep" -> FilteredGenerator(
                BrownNoiseGenerator(stepScale = 0.028f, amplitude = 9500f),
                sampleRate, lowPassCutoffHz = 900f
            )

            // ---- Green noise family (mid-frequency emphasis) ----
            "green_noise_classic" -> FilteredGenerator(
                PinkNoiseGenerator(amplitude = 9500f),
                sampleRate, lowPassCutoffHz = 2200f, highPassCutoffHz = 250f
            )
            "forest_ambient" -> BirdChirpGenerator(
                sampleRate,
                ambient = FilteredGenerator(
                    PinkNoiseGenerator(amplitude = 5500f),
                    sampleRate, lowPassCutoffHz = 2400f
                ),
                chirpPerSecond = 0.35f
            )
            "meadow_wind" -> FilteredGenerator(
                PinkNoiseGenerator(amplitude = 9000f),
                sampleRate, lowPassCutoffHz = 1400f, highPassCutoffHz = 80f
            )

            // ---- Rain family ----
            "light_rain" -> RainGenerator(baseGain = 0.7f, impulseChance = 0.00025f, impulseMin = -4000, impulseMax = 4000)
            "heavy_rain" -> RainGenerator(baseGain = 0.95f, impulseChance = 0.0012f)
            "rain_on_roof" -> FilteredGenerator(
                RainGenerator(baseGain = 0.95f, impulseChance = 0.0015f, impulseMin = -9500, impulseMax = 9500),
                sampleRate, lowPassCutoffHz = 1500f
            )
            "rain_on_window" -> FilteredGenerator(
                RainGenerator(baseGain = 0.75f, impulseChance = 0.0006f, impulseMin = -6000, impulseMax = 6000),
                sampleRate, highPassCutoffHz = 800f
            )
            "thunderstorm" -> RainGenerator(baseGain = 1.0f, impulseChance = 0.002f, impulseMin = -12000, impulseMax = 12000)

            // ---- Nature family ----
            "ocean_waves" -> OceanWaveGenerator(sampleRate = sampleRate)
            "river_stream" -> FilteredGenerator(
                RainGenerator(baseGain = 0.8f, impulseChance = 0.00015f),
                sampleRate, highPassCutoffHz = 1500f
            )
            "waterfall" -> FilteredGenerator(
                RainGenerator(baseGain = 1.0f, impulseChance = 0f, impulseMin = 0, impulseMax = 1),
                sampleRate, lowPassCutoffHz = 2400f, highPassCutoffHz = 400f
            )
            "birds_morning" -> BirdChirpGenerator(sampleRate, chirpPerSecond = 1.4f)
            "crickets_night" -> CricketGenerator(sampleRate, rate = 3.5f)
            "campfire" -> CrackleGenerator(sampleRate, cracklesPerSecond = 5f)

            // ---- ASMR family (different timbres each) ----
            "asmr_whisper" -> FilteredGenerator(
                PinkNoiseGenerator(amplitude = 5500f),
                sampleRate, lowPassCutoffHz = 5000f, highPassCutoffHz = 1500f
            )
            "asmr_tapping" -> CrackleGenerator(sampleRate, cracklesPerSecond = 2.2f,
                ambient = PinkNoiseGenerator(amplitude = 1500f))
            "asmr_page_turning" -> CrackleGenerator(sampleRate, cracklesPerSecond = 0.8f,
                ambient = FilteredGenerator(WhiteNoiseGenerator(amplitude = 3500), sampleRate, lowPassCutoffHz = 4500f))
            "asmr_brushing" -> FilteredGenerator(
                WhiteNoiseGenerator(amplitude = 6500),
                sampleRate, lowPassCutoffHz = 3500f, highPassCutoffHz = 1000f
            )

            // ---- Meditation ----
            "meditation_breathing" -> TonalLullabyGenerator(sampleRate, rootHz = 110f, breathRateHz = 0.08f, amplitude = 3200f)
            "meditation_body_scan" -> TonalLullabyGenerator(sampleRate, rootHz = 87.31f, breathRateHz = 0.10f, amplitude = 3000f)
            "meditation_mindfulness" -> TonalLullabyGenerator(sampleRate, rootHz = 146.83f, breathRateHz = 0.06f, amplitude = 2800f)
            "singing_bowls" -> HumGenerator(
                inner = null,
                sampleRate = sampleRate, humHz = 196f, humAmplitude = 2400f
            )

            // ---- Stories ---- (themed ambience blended with a soft melodic bed)
            "story_enchanted_forest" -> StoryAmbienceGenerator(
                sampleRate,
                ambient = BirdChirpGenerator(
                    sampleRate, chirpPerSecond = 0.4f,
                    ambient = PinkNoiseGenerator(amplitude = 4500f)
                ),
                lullaby = TonalLullabyGenerator(sampleRate, rootHz = 130.81f, breathRateHz = 0.07f, amplitude = 2400f),
                ambientGain = 0.6f
            )
            "story_ocean_voyage" -> StoryAmbienceGenerator(
                sampleRate,
                ambient = OceanWaveGenerator(sampleRate = sampleRate, periodSec = 8.0),
                lullaby = TonalLullabyGenerator(sampleRate, rootHz = 110f, breathRateHz = 0.06f, amplitude = 2200f),
                ambientGain = 0.7f
            )
            "story_stargazing" -> StoryAmbienceGenerator(sampleRate)
            "story_mountain_lodge" -> StoryAmbienceGenerator(
                sampleRate,
                ambient = CrackleGenerator(sampleRate, cracklesPerSecond = 1.4f),
                lullaby = TonalLullabyGenerator(sampleRate, rootHz = 98f, breathRateHz = 0.05f, amplitude = 2400f),
                ambientGain = 0.7f
            )

            // ---- Music ---- (raised amplitudes so sleep music sits at a comfortable level
            // closer to the ambient/noise tracks instead of being noticeably quieter)
            "music_piano" -> TonalLullabyGenerator(sampleRate, rootHz = 261.63f, amplitude = 6500f)
            "music_ambient" -> TonalLullabyGenerator(sampleRate, rootHz = 174.61f, breathRateHz = 0.07f, amplitude = 6000f)
            "music_lullaby" -> TonalLullabyGenerator(sampleRate, rootHz = 196f, breathRateHz = 0.10f, amplitude = 6800f)
            "music_binaural" -> HumGenerator(
                inner = TonalLullabyGenerator(sampleRate, rootHz = 100f, breathRateHz = 0.05f, amplitude = 3200f),
                sampleRate = sampleRate, humHz = 4f, humAmplitude = 1400f, innerGain = 1.2f
            )

            else -> when (categoryId) {
                "white_noise" -> WhiteNoiseGenerator(amplitude = 9000)
                "pink_noise" -> PinkNoiseGenerator(amplitude = 7500f)
                "green_noise" -> FilteredGenerator(PinkNoiseGenerator(amplitude = 7000f), sampleRate, lowPassCutoffHz = 2200f, highPassCutoffHz = 250f)
                "rain" -> RainGenerator()
                "nature" -> OceanWaveGenerator(sampleRate = sampleRate, periodSec = 8.0)
                "asmr" -> PinkNoiseGenerator(amplitude = 6000f)
                "meditation" -> TonalLullabyGenerator(sampleRate)
                "stories" -> StoryAmbienceGenerator(sampleRate)
                "music" -> TonalLullabyGenerator(sampleRate, rootHz = 220f, amplitude = 6500f)
                else -> PinkNoiseGenerator()
            }
        }
    }
}

