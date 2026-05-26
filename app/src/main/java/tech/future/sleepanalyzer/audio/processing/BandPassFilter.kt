package tech.future.sleepanalyzer.audio.processing

import tech.future.sleepanalyzer.audio.AudioFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Biquad band-pass filter implemented as a single AudioProcessor.
 * Defaults target the human vocal range (~85 Hz to ~3400 Hz),
 * suppressing fans, AC hum, and high-frequency hiss.
 *
 * State is per-instance and thread-confined; create one per consumer.
 */
class BandPassFilter(
    private val sampleRate: Int,
    private val lowHz: Float = 80f,
    private val highHz: Float = 3400f,
    private val q: Float = 0.707f
) : AudioProcessor {

    private val a0: Float; private val a1: Float; private val a2: Float
    private val b1: Float; private val b2: Float

    init {
        val centerHz = sqrt(lowHz * highHz)
        val bw = (highHz - lowHz).coerceAtLeast(50f)
        val effQ = (centerHz / bw).coerceIn(0.1f, 10f)
        val omega = 2.0 * PI * centerHz / sampleRate
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2 * effQ)
        val norm = 1.0 + alpha
        a0 = (alpha / norm).toFloat()
        a1 = 0f
        a2 = (-alpha / norm).toFloat()
        b1 = ((-2.0 * cs) / norm).toFloat()
        b2 = ((1.0 - alpha) / norm).toFloat()
    }

    private var x1 = 0f; private var x2 = 0f
    private var y1 = 0f; private var y2 = 0f

    override fun process(frame: AudioFrame): AudioFrame {
        val out = ShortArray(frame.length)
        val src = frame.samples
        for (i in 0 until frame.length) {
            val x0 = src[i].toFloat()
            val y0 = a0 * x0 + a1 * x1 + a2 * x2 - b1 * y1 - b2 * y2
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
            out[i] = y0.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return frame.copy(samples = out)
    }
}
