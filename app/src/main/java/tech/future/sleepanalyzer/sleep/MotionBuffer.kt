package tech.future.sleepanalyzer.sleep

import java.util.ArrayDeque

/**
 * Thread-safe ring buffer of [MotionSample]s. Holds at most [capacity] samples;
 * older entries are evicted as new ones arrive.
 *
 * Used to decouple motion producers (SleepTrackingService, SmartWakeService) from
 * consumers (SleepStageEstimator, SmartWakeAnalyzer, charts).
 */
class MotionBuffer(val capacity: Int = 4096) {
    private val deque = ArrayDeque<MotionSample>(capacity)

    val size: Int get() = synchronized(deque) { deque.size }
    val isEmpty: Boolean get() = synchronized(deque) { deque.isEmpty() }

    fun add(sample: MotionSample) = synchronized(deque) {
        if (deque.size == capacity) deque.pollFirst()
        deque.addLast(sample)
    }

    fun snapshot(): List<MotionSample> = synchronized(deque) { deque.toList() }

    /** Returns samples whose timestamp is >= [sinceMs]. */
    fun snapshotSince(sinceMs: Long): List<MotionSample> = synchronized(deque) {
        deque.dropWhile { it.timestampMs < sinceMs }.toList()
    }

    fun clear() = synchronized(deque) { deque.clear() }
}
