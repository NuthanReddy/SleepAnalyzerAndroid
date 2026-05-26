package tech.future.sleepanalyzer.audio.analysis

import android.os.SystemClock

class MovementBurstDetector {
    private var floor = 200f
    private var lastBurstMs = 0L
    private var burstCount = 0

    fun feed(rms: Float): Boolean {
        val threshold = floor * 4f
        if (rms <= threshold || rms <= 600f) {
            floor = floor * 0.97f + rms * 0.03f
            return false
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastBurstMs < BURST_SUPPRESSION_MS) return false

        lastBurstMs = now
        burstCount++
        return true
    }

    fun burstsThenReset(): Int = burstCount.also { burstCount = 0 }

    fun reset() {
        floor = 200f
        lastBurstMs = 0L
        burstCount = 0
    }

    private companion object {
        const val BURST_SUPPRESSION_MS = 500L
    }
}
