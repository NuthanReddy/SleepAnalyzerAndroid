package tech.future.sleepanalyzer.audio.analysis

import tech.future.sleepanalyzer.audio.AudioEventType
import tech.future.sleepanalyzer.sleep.MicSleepSignal

class MicSleepSignalAggregator(
    private val sampleRate: Int,
    private val windowMs: Long = 60_000L
) {
    private val burstDetector = MovementBurstDetector()
    private val breathing = BreathingExtractor(rmsHz = RMS_HZ)
    private val rmsCapacity = ((windowMs / RMS_BIN_MS).toInt()).coerceAtLeast(1)
    private val rmsSeries = ArrayDeque<Float>(rmsCapacity)
    private var silentFrames = 0
    private var totalFrames = 0
    private var snoreEvents = 0
    private var talkEvents = 0
    private var coughEvents = 0
    private var windowStartMs = System.currentTimeMillis()

    @Volatile
    private var latest: MicSleepSignal? = null

    @Synchronized
    fun reset() {
        rmsSeries.clear()
        silentFrames = 0
        totalFrames = 0
        snoreEvents = 0
        talkEvents = 0
        coughEvents = 0
        windowStartMs = System.currentTimeMillis()
        latest = null
        burstDetector.reset()
    }

    /** Call once per frame from the audio pipeline. */
    @Synchronized
    fun onFrame(rms: Float, isVoice: Boolean) {
        totalFrames++
        if (!isVoice) silentFrames++
        if (totalFrames % FRAMES_PER_RMS_BIN == 0) {
            rmsSeries.addLast(rms)
            while (rmsSeries.size > rmsCapacity) rmsSeries.removeFirst()
        }
        burstDetector.feed(rms)
    }

    @Synchronized
    fun onEvent(type: AudioEventType) {
        when (type) {
            AudioEventType.SNORE -> snoreEvents++
            AudioEventType.TALK -> talkEvents++
            AudioEventType.COUGH -> coughEvents++
            else -> Unit
        }
    }

    /** Called once per minute. Computes the window aggregate and resets counters. */
    @Synchronized
    fun rollover(): MicSleepSignal {
        val now = System.currentTimeMillis()
        val elapsedMin = ((now - windowStartMs) / 60_000.0).coerceAtLeast(0.001)
        val (rateBpm, regularity) = if (rmsSeries.size > 60) {
            breathing.extract(copyRmsSeries())
        } else {
            null to null
        }

        val signal = MicSleepSignal(
            breathingRateRpm = rateBpm,
            breathingRegularity = regularity,
            silenceRatio = if (totalFrames > 0) silentFrames.toFloat() / totalFrames else 0f,
            movementBurstsPerMinute = (burstDetector.burstsThenReset() / elapsedMin).toFloat(),
            snoreEventsPerMinute = (snoreEvents / elapsedMin).toFloat(),
            talkEventsPerMinute = (talkEvents / elapsedMin).toFloat(),
            coughEventsPerMinute = (coughEvents / elapsedMin).toFloat(),
            windowSampleCount = totalFrames
        )
        latest = signal

        silentFrames = 0
        totalFrames = 0
        snoreEvents = 0
        talkEvents = 0
        coughEvents = 0
        windowStartMs = now
        return signal
    }

    fun latest(): MicSleepSignal? = latest

    private fun copyRmsSeries(): FloatArray {
        val copy = FloatArray(rmsSeries.size)
        var index = 0
        for (value in rmsSeries) {
            copy[index++] = value
        }
        return copy
    }

    private companion object {
        const val RMS_HZ = 10f
        const val RMS_BIN_MS = 100L
        const val FRAMES_PER_RMS_BIN = 3
    }
}
