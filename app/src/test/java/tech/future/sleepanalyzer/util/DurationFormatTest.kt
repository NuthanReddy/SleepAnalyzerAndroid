package tech.future.sleepanalyzer.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [formatSleepDuration]. Sessions are stored as whole minutes but the UI wants
 * seconds precision for short sessions, so the formatter switches units based on magnitude. These
 * cover the unit boundaries plus the degenerate/negative inputs.
 */
class DurationFormatTest {

    private fun ms(hours: Long = 0, minutes: Long = 0, seconds: Long = 0, millis: Long = 0): Long =
        hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis

    @Test
    fun `hours and minutes shown when at least an hour`() {
        assertEquals("2h 30m", formatSleepDuration(ms(hours = 2, minutes = 30)))
    }

    @Test
    fun `seconds are dropped once hours are present`() {
        // 1h 5m 45s -> seconds are not part of the hour format.
        assertEquals("1h 5m", formatSleepDuration(ms(hours = 1, minutes = 5, seconds = 45)))
    }

    @Test
    fun `exact hour shows zero minutes`() {
        assertEquals("1h 0m", formatSleepDuration(ms(hours = 1)))
    }

    @Test
    fun `minutes and seconds shown when under an hour`() {
        assertEquals("5m 20s", formatSleepDuration(ms(minutes = 5, seconds = 20)))
    }

    @Test
    fun `exactly one minute shows minutes and seconds`() {
        assertEquals("1m 0s", formatSleepDuration(ms(minutes = 1)))
    }

    @Test
    fun `seconds only when under a minute`() {
        assertEquals("42s", formatSleepDuration(ms(seconds = 42)))
    }

    @Test
    fun `sub-second durations floor to zero seconds`() {
        assertEquals("0s", formatSleepDuration(500L))
    }

    @Test
    fun `zero duration formats as zero seconds`() {
        assertEquals("0s", formatSleepDuration(0L))
    }

    @Test
    fun `negative durations are clamped to zero`() {
        assertEquals("0s", formatSleepDuration(-5_000L))
    }

    @Test
    fun `long overnight sessions format correctly`() {
        assertEquals("8h 15m", formatSleepDuration(ms(hours = 8, minutes = 15)))
    }
}
