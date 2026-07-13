package tech.future.sleepanalyzer.audio.processing

import tech.future.sleepanalyzer.audio.util.AudioMath
import tech.future.sleepanalyzer.audio.util.FftProcessor
import kotlin.math.sqrt

/**
 * Streaming single-channel spectral-subtraction denoiser.
 *
 * Bedrooms almost always carry a steady background bed of noise - an AC unit, a ceiling fan, a
 * fridge hum, a white-noise machine. That noise is *stationary* (its spectrum barely changes over
 * time), which is exactly the case classic spectral subtraction handles well: we learn the noise
 * magnitude spectrum during quiet stretches and subtract it from every frame, leaving snores,
 * coughs, and speech far more prominent for the downstream VAD and classifier.
 *
 * The processor keeps a rolling STFT (analysis-Hann window, 50% overlap-add) and returns exactly as
 * many output samples as it is fed, so it can be dropped in as a transparent stream filter. The
 * only visible effect is a fixed [fftSize]-sample latency, which is negligible (~46 ms at 22 kHz)
 * for second-scale sleep events.
 *
 * Crucially, the subtraction is *event-aware*: when a block's energy is close to the learned noise
 * floor (a steady stretch of AC/fan hum) the full over-subtraction is applied, but the moment a
 * block jumps well above that floor - a cough, snore, or sleep-talk burst - the subtraction backs
 * off so the transient is preserved for the VAD and classifier instead of being gutted. Flat
 * spectral subtraction erases broadband events like coughs, whose per-bin energy sits only modestly
 * above the noise; gating on block energy protects them while still cleaning the quiet background.
 *
 * State is per-instance and thread-confined - create one per capture stream and never share it.
 *
 * @param overSubtraction how aggressively the noise estimate is removed on steady-noise blocks
 *   (1.0 = exact estimate; >1 over-subtracts to better kill residual hum at the cost of possible
 *   distortion). This is eased down automatically on blocks that look like an acoustic event.
 * @param spectralFloor fraction of the original magnitude retained in every bin, so we never fully
 *   null a bin. This suppresses "musical noise" artifacts and keeps the residual natural enough for
 *   a learned classifier (YAMNet) to still recognise events.
 */
class SpectralNoiseReducer(
    @Suppress("UNUSED_PARAMETER") sampleRate: Int,
    private val fftSize: Int = DEFAULT_FFT_SIZE,
    private val overSubtraction: Float = DEFAULT_OVER_SUBTRACTION,
    private val spectralFloor: Float = DEFAULT_SPECTRAL_FLOOR,
    private val warmupBlocks: Int = DEFAULT_WARMUP_BLOCKS
) {

    init {
        require(fftSize >= 2 && (fftSize and (fftSize - 1)) == 0) { "fftSize must be a power of two" }
    }

    private val hop = fftSize / 2
    private val halfBins = fftSize / 2
    private val fft = FftProcessor.forSize(fftSize)
    private val window = AudioMath.hannWindow(fftSize)

    /**
     * Per-output-sample normalization for analysis-window-only overlap-add. Dividing the summed
     * blocks by this yields unity-gain reconstruction when the spectral gain is 1, i.e. the filter
     * is bit-for-bit transparent when there is nothing to subtract.
     */
    private val olaNorm = FloatArray(hop) { i ->
        (window[i] + window[i + hop]).let { if (it < 1e-4f) 1e-4f else it }
    }

    // Reusable per-hop scratch (avoids per-frame allocation / GC churn on the capture thread).
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    private val mag = FloatArray(halfBins + 1)
    private val history = FloatArray(fftSize)
    private val acc = FloatArray(fftSize)

    private val noiseMag = FloatArray(halfBins + 1)
    private var noiseRms = 0f
    private var blocksSeen = 0

    // How far the current block's energy sits above the learned noise floor (1 = at the floor).
    // Refreshed every block in updateNoiseEstimate() and read by applyGains() to ease off the
    // subtraction on acoustic events (coughs/snores) while fully cleaning steady background blocks.
    private var blockEventRatio = 1f

    private var inbox = FloatArray(fftSize * 2)
    private var inboxHead = 0
    private var inboxSize = 0

    private var outbox = FloatArray(fftSize * 4)
    private var outboxHead = 0
    private var outboxSize = 0

    init { seedLatency() }

    /**
     * Denoise [length] samples from [samples] and return the same number of cleaned 16-bit samples.
     * Output for the first [fftSize] samples is primed with silence to cover the STFT latency, so
     * callers always get a full-length result and downstream timing stays aligned.
     */
    fun process(samples: ShortArray, length: Int = samples.size): ShortArray {
        if (length <= 0) return ShortArray(0)
        for (i in 0 until length) pushIn(samples[i] / 32768f)
        while (inboxSize >= hop) advanceBlock()

        val out = ShortArray(length)
        for (j in 0 until length) {
            val v = popOut() * 32768f
            out[j] = v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /** Clears all learned noise and buffered state so the instance can be reused for a new stream. */
    fun reset() {
        re.fill(0f); im.fill(0f); mag.fill(0f)
        history.fill(0f); acc.fill(0f); noiseMag.fill(0f)
        noiseRms = 0f; blocksSeen = 0
        blockEventRatio = 1f
        inboxHead = 0; inboxSize = 0
        outboxHead = 0; outboxSize = 0
        seedLatency()
    }

    private fun advanceBlock() {
        System.arraycopy(history, hop, history, 0, fftSize - hop)
        for (k in 0 until hop) history[fftSize - hop + k] = popIn()

        for (k in 0 until fftSize) { re[k] = history[k] * window[k]; im[k] = 0f }
        fft.fftInPlace(re, im)
        for (k in 0..halfBins) mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])

        updateNoiseEstimate()
        applyGains()

        // Inverse FFT via the forward transform: x[n] = Re(FFT(conj(X)))/N.
        for (k in 0 until fftSize) im[k] = -im[k]
        fft.fftInPlace(re, im)
        val invN = 1f / fftSize
        for (k in 0 until fftSize) acc[k] += re[k] * invN

        for (k in 0 until hop) pushOut(acc[k] / olaNorm[k])
        System.arraycopy(acc, hop, acc, 0, fftSize - hop)
        for (k in fftSize - hop until fftSize) acc[k] = 0f
    }

    private fun updateNoiseEstimate() {
        var energy = 0.0
        for (k in 0..halfBins) energy += mag[k].toDouble() * mag[k]
        val blockRms = sqrt(energy / (halfBins + 1)).toFloat()

        blocksSeen++
        // How loud this block is relative to the tracked noise floor. Computed before the early
        // return below so applyGains() sees a fresh value even on skipped (event) blocks.
        blockEventRatio = if (noiseRms > 1e-6f) blockRms / noiseRms else 1f

        val warming = blocksSeen <= warmupBlocks
        // Only learn from blocks that look like background: the warm-up window (assumed quiet at the
        // start of a night) plus any later block whose energy is close to the tracked noise level.
        // Loud blocks (snores/talk) are skipped so an event never inflates the noise profile.
        val isBackground = warming || noiseRms <= 0f || blockRms <= noiseRms * ACTIVATION_FACTOR
        if (!isBackground) return

        val rate = if (warming) WARMUP_ADAPT else STEADY_ADAPT
        for (k in 0..halfBins) noiseMag[k] = noiseMag[k] * (1f - rate) + mag[k] * rate
        noiseRms = if (noiseRms <= 0f) blockRms else noiseRms * (1f - rate) + blockRms * rate
    }

    private fun applyGains() {
        // Scale the over-subtraction down for blocks that look like an acoustic event so broadband
        // transients (coughs) keep most of their energy instead of being pulled to the floor.
        val alpha = overSubtraction * eventSubtractionScale(blockEventRatio)
        for (k in 0..halfBins) {
            val m = mag[k]
            val gain = if (m > 1e-9f) {
                val subtracted = m - alpha * noiseMag[k]
                val floor = spectralFloor * m
                val kept = if (subtracted > floor) subtracted else floor
                (kept / m).coerceIn(0f, 1f)
            } else 1f
            re[k] *= gain; im[k] *= gain
            if (k in 1 until halfBins) {
                val mirror = fftSize - k
                re[mirror] *= gain; im[mirror] *= gain
            }
        }
    }

    /**
     * Maps a block's energy-above-noise ratio to an over-subtraction multiplier: full strength for
     * steady background blocks (ratio at/below [EVENT_LOW]), sharply reduced for clear events
     * (ratio at/above [EVENT_HIGH]), linearly interpolated in between.
     */
    private fun eventSubtractionScale(ratio: Float): Float = when {
        ratio <= EVENT_LOW -> 1f
        ratio >= EVENT_HIGH -> EVENT_SUBTRACTION_SCALE
        else -> {
            val t = (ratio - EVENT_LOW) / (EVENT_HIGH - EVENT_LOW)
            1f + t * (EVENT_SUBTRACTION_SCALE - 1f)
        }
    }

    private fun seedLatency() { repeat(fftSize) { pushOut(0f) } }

    private fun pushIn(v: Float) {
        if (inboxSize == inbox.size) inbox = grow(inbox, inboxHead, inboxSize).also { inboxHead = 0 }
        inbox[(inboxHead + inboxSize) % inbox.size] = v
        inboxSize++
    }

    private fun popIn(): Float {
        val v = inbox[inboxHead]
        inboxHead = (inboxHead + 1) % inbox.size
        inboxSize--
        return v
    }

    private fun pushOut(v: Float) {
        if (outboxSize == outbox.size) outbox = grow(outbox, outboxHead, outboxSize).also { outboxHead = 0 }
        outbox[(outboxHead + outboxSize) % outbox.size] = v
        outboxSize++
    }

    private fun popOut(): Float {
        if (outboxSize == 0) return 0f
        val v = outbox[outboxHead]
        outboxHead = (outboxHead + 1) % outbox.size
        outboxSize--
        return v
    }

    private fun grow(src: FloatArray, head: Int, size: Int): FloatArray {
        val bigger = FloatArray(src.size * 2)
        for (k in 0 until size) bigger[k] = src[(head + k) % src.size]
        return bigger
    }

    companion object {
        const val DEFAULT_FFT_SIZE = 1024
        const val DEFAULT_OVER_SUBTRACTION = 1.5f
        const val DEFAULT_SPECTRAL_FLOOR = 0.12f
        const val DEFAULT_WARMUP_BLOCKS = 16

        private const val ACTIVATION_FACTOR = 1.8f
        private const val WARMUP_ADAPT = 0.25f
        private const val STEADY_ADAPT = 0.05f

        // Event gating for over-subtraction: at/below EVENT_LOW a block is treated as steady
        // background (full subtraction); at/above EVENT_HIGH it is a clear acoustic event and the
        // subtraction is scaled to EVENT_SUBTRACTION_SCALE so the transient survives.
        private const val EVENT_LOW = 1.4f
        private const val EVENT_HIGH = 2.2f
        private const val EVENT_SUBTRACTION_SCALE = 0.15f
    }
}
