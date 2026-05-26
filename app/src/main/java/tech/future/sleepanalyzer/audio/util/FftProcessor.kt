package tech.future.sleepanalyzer.audio.util

import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Radix-2 Cooley-Tukey FFT with reusable buffer pooling.
 * Single-precision floats are used to minimize allocation cost and GC churn.
 */
class FftProcessor(val size: Int) {
    init { require(size > 0 && (size and (size - 1)) == 0) { "FFT size must be a power of two" } }

    private val cosTable = FloatArray(size / 2)
    private val sinTable = FloatArray(size / 2)
    private val bitRev = IntArray(size)

    init {
        for (i in 0 until size / 2) {
            val theta = -2.0 * PI * i / size
            cosTable[i] = cos(theta).toFloat()
            sinTable[i] = sin(theta).toFloat()
        }
        val bits = 31 - Integer.numberOfLeadingZeros(size)
        for (i in 0 until size) {
            var x = i; var r = 0
            for (b in 0 until bits) { r = (r shl 1) or (x and 1); x = x ushr 1 }
            bitRev[i] = r
        }
    }

    /**
     * In-place complex FFT. Input/output: re/im float arrays of length [size].
     */
    fun fftInPlace(re: FloatArray, im: FloatArray) {
        for (i in 0 until size) {
            val j = bitRev[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val tableStep = size / len
            var i = 0
            while (i < size) {
                var k = 0
                for (j in i until i + half) {
                    val cosV = cosTable[k]
                    val sinV = sinTable[k]
                    val tre = re[j + half] * cosV - im[j + half] * sinV
                    val tim = re[j + half] * sinV + im[j + half] * cosV
                    re[j + half] = re[j] - tre; im[j + half] = im[j] - tim
                    re[j] += tre; im[j] += tim
                    k += tableStep
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Compute magnitude spectrum (bins 0..size/2) from real input.
     * Output array must be size/2 + 1. Reuses internal buffers from a pool.
     */
    fun magnitudeSpectrum(samples: FloatArray, out: FloatArray) {
        require(samples.size == size && out.size == size / 2 + 1)
        val (re, im) = borrowBuffers()
        try {
            System.arraycopy(samples, 0, re, 0, size)
            for (i in 0 until size) im[i] = 0f
            fftInPlace(re, im)
            for (i in 0..size / 2) {
                val a = re[i]; val b = im[i]
                out[i] = kotlin.math.sqrt(a * a + b * b)
            }
        } finally {
            recycleBuffers(re, im)
        }
    }

    private val bufferPool = ConcurrentLinkedDeque<Pair<FloatArray, FloatArray>>()
    private fun borrowBuffers(): Pair<FloatArray, FloatArray> {
        return bufferPool.pollFirst() ?: (FloatArray(size) to FloatArray(size))
    }
    private fun recycleBuffers(re: FloatArray, im: FloatArray) {
        if (bufferPool.size < 4) bufferPool.addFirst(re to im)
    }

    companion object {
        private val instances = java.util.concurrent.ConcurrentHashMap<Int, FftProcessor>()
        fun forSize(size: Int): FftProcessor =
            instances.computeIfAbsent(size) { FftProcessor(it) }
    }
}
