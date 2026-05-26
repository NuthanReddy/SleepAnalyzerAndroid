package tech.future.sleepanalyzer.audio.util

/**
 * Lock-free single-producer single-consumer ring buffer over a backing short array,
 * used to keep the latest N seconds of raw PCM samples for cropping after an event triggers.
 */
class ShortRingBuffer(private val capacity: Int) {
    private val buffer = ShortArray(capacity)
    private var writePos = 0
    private var size = 0

    val isEmpty: Boolean get() = synchronized(this) { size == 0 }
    val available: Int get() = synchronized(this) { size }

    fun write(src: ShortArray, offset: Int, length: Int) {
        if (length <= 0) return
        synchronized(this) {
            var written = 0
            while (written < length) {
                val chunk = minOf(capacity - writePos, length - written)
                System.arraycopy(src, offset + written, buffer, writePos, chunk)
                writePos = (writePos + chunk) % capacity
                written += chunk
            }
            size = minOf(capacity, size + length)
        }
    }

    /**
     * Returns a snapshot of the last [count] samples in chronological order.
     * If fewer than [count] samples are buffered, returns all available.
     */
    fun snapshotLast(count: Int): ShortArray {
        synchronized(this) {
            val take = minOf(count, size)
            val out = ShortArray(take)
            // Start position is writePos - take (mod capacity)
            var start = (writePos - take + capacity) % capacity
            var copied = 0
            while (copied < take) {
                val chunk = minOf(capacity - start, take - copied)
                System.arraycopy(buffer, start, out, copied, chunk)
                start = (start + chunk) % capacity
                copied += chunk
            }
            return out
        }
    }

    fun clear() {
        synchronized(this) {
            writePos = 0
            size = 0
        }
    }
}
