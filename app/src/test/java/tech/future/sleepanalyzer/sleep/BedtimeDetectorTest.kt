package tech.future.sleepanalyzer.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BedtimeDetectorTest {

    private val minute = 60_000L

    private fun still(tsMs: Long, screenOn: Boolean = false, mag: Float = 0.1f, micQuiet: Boolean? = null) =
        BedtimeSignals(timestampMs = tsMs, screenOn = screenOn, motionMagnitude = mag, micQuiet = micQuiet)

    @Test
    fun `screen on yields idle and never accumulates`() {
        val detector = BedtimeDetector(quietWindowMinutes = 15)
        val r = detector.onSignals(still(tsMs = 0, screenOn = true))
        assertEquals(BedtimeDetection.Idle, r)
    }

    @Test
    fun `accumulates then triggers exactly at the window boundary`() {
        val detector = BedtimeDetector(quietWindowMinutes = 15)
        assertTrue(detector.onSignals(still(0)) is BedtimeDetection.Accumulating)
        assertTrue(detector.onSignals(still(10 * minute)) is BedtimeDetection.Accumulating)
        // 14:59 -> not yet
        assertTrue(detector.onSignals(still(15 * minute - 1)) is BedtimeDetection.Accumulating)
        // 15:00 -> trigger
        assertEquals(BedtimeDetection.Triggered, detector.onSignals(still(15 * minute)))
    }

    @Test
    fun `remaining time counts down as the window fills`() {
        val detector = BedtimeDetector(quietWindowMinutes = 15)
        detector.onSignals(still(0))
        val mid = detector.onSignals(still(5 * minute)) as BedtimeDetection.Accumulating
        assertEquals(5 * minute, mid.elapsedMs)
        assertEquals(10 * minute, mid.remainingMs)
    }

    @Test
    fun `motion spike resets the window so no trigger`() {
        val detector = BedtimeDetector(quietWindowMinutes = 15)
        detector.onSignals(still(0))
        detector.onSignals(still(10 * minute))
        // big movement resets
        assertEquals(BedtimeDetection.Idle, detector.onSignals(still(11 * minute, mag = 3.0f)))
        // fresh window starts here; 15 min later from the reset is needed
        assertTrue(detector.onSignals(still(12 * minute)) is BedtimeDetection.Accumulating)
        assertTrue(detector.onSignals(still(26 * minute)) is BedtimeDetection.Accumulating)
        assertEquals(BedtimeDetection.Triggered, detector.onSignals(still(27 * minute)))
    }

    @Test
    fun `screen turning on mid-window resets`() {
        val detector = BedtimeDetector(quietWindowMinutes = 15)
        detector.onSignals(still(0))
        assertEquals(BedtimeDetection.Idle, detector.onSignals(still(10 * minute, screenOn = true)))
        assertTrue(detector.onSignals(still(11 * minute)) is BedtimeDetection.Accumulating)
    }

    @Test
    fun `mic not quiet prevents trigger`() {
        val detector = BedtimeDetector(quietWindowMinutes = 15)
        detector.onSignals(still(0, micQuiet = true))
        assertEquals(BedtimeDetection.Idle, detector.onSignals(still(10 * minute, micQuiet = false)))
    }

    @Test
    fun `null mic is ignored so screen-off and still alone can trigger`() {
        val detector = BedtimeDetector(quietWindowMinutes = 15)
        detector.onSignals(still(0, micQuiet = null))
        assertEquals(BedtimeDetection.Triggered, detector.onSignals(still(15 * minute, micQuiet = null)))
    }

    @Test
    fun `does not double-trigger while conditions persist`() {
        val detector = BedtimeDetector(quietWindowMinutes = 15)
        detector.onSignals(still(0))
        assertEquals(BedtimeDetection.Triggered, detector.onSignals(still(15 * minute)))
        val after = detector.onSignals(still(16 * minute))
        assertTrue(after is BedtimeDetection.Accumulating)
        assertEquals(0L, (after as BedtimeDetection.Accumulating).remainingMs)
    }

    @Test
    fun `re-triggers after conditions break and a fresh full window`() {
        val detector = BedtimeDetector(quietWindowMinutes = 15)
        detector.onSignals(still(0))
        assertEquals(BedtimeDetection.Triggered, detector.onSignals(still(15 * minute)))
        // user gets up
        assertEquals(BedtimeDetection.Idle, detector.onSignals(still(20 * minute, screenOn = true)))
        // back to bed; needs a fresh 15 min
        detector.onSignals(still(21 * minute))
        assertEquals(BedtimeDetection.Triggered, detector.onSignals(still(36 * minute)))
    }

    @Test
    fun `custom quiet window is honored`() {
        val detector = BedtimeDetector(quietWindowMinutes = 5)
        detector.onSignals(still(0))
        assertTrue(detector.onSignals(still(4 * minute)) is BedtimeDetection.Accumulating)
        assertEquals(BedtimeDetection.Triggered, detector.onSignals(still(5 * minute)))
    }

    @Test
    fun `reset clears an in-progress window`() {
        val detector = BedtimeDetector(quietWindowMinutes = 15)
        detector.onSignals(still(0))
        detector.onSignals(still(10 * minute))
        detector.reset()
        // window restarts from here
        assertTrue(detector.onSignals(still(11 * minute)) is BedtimeDetection.Accumulating)
        assertTrue(detector.onSignals(still(25 * minute)) is BedtimeDetection.Accumulating)
        assertEquals(BedtimeDetection.Triggered, detector.onSignals(still(26 * minute)))
    }
}
