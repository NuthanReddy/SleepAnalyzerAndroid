package tech.future.sleepanalyzer.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.future.sleepanalyzer.data.db.entity.AlarmConfig
import java.util.Calendar
import java.util.TimeZone

class AlarmTimeCalculatorTest {

    private fun cal(tz: String, y: Int, month0: Int, day: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone(tz)).apply {
            clear()
            set(y, month0, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun alarm(
        hour: Int,
        minute: Int,
        days: String = "",
        wakeWindow: Int = 30
    ) = AlarmConfig(hour = hour, minute = minute, daysOfWeek = days, wakeWindowMinutes = wakeWindow)

    @Test
    fun `fires later same day when time has not passed`() {
        val now = cal("UTC", 2026, Calendar.JANUARY, 5, 6, 0)
        val result = AlarmTimeCalculator.nextTriggerMillis(alarm(7, 30), now, subtractWakeWindow = false)
        assertEquals(cal("UTC", 2026, Calendar.JANUARY, 5, 7, 30).timeInMillis, result)
    }

    @Test
    fun `rolls to next day when time already passed`() {
        val now = cal("UTC", 2026, Calendar.JANUARY, 5, 8, 0)
        val result = AlarmTimeCalculator.nextTriggerMillis(alarm(7, 0), now, subtractWakeWindow = false)
        assertEquals(cal("UTC", 2026, Calendar.JANUARY, 6, 7, 0).timeInMillis, result)
    }

    @Test
    fun `subtracting wake window returns window start before deadline`() {
        val now = cal("UTC", 2026, Calendar.JANUARY, 5, 6, 0)
        val deadline = AlarmTimeCalculator.nextTriggerMillis(alarm(7, 30, wakeWindow = 30), now, subtractWakeWindow = false)
        val windowStart = AlarmTimeCalculator.nextTriggerMillis(alarm(7, 30, wakeWindow = 30), now, subtractWakeWindow = true)
        assertEquals(30L * 60 * 1000, deadline - windowStart)
        assertEquals(cal("UTC", 2026, Calendar.JANUARY, 5, 7, 0).timeInMillis, windowStart)
    }

    @Test
    fun `picks next enabled weekday`() {
        // Monday 2026-01-05 08:00; only Wednesday (our day 3) enabled.
        val now = cal("UTC", 2026, Calendar.JANUARY, 5, 8, 0)
        val resultMs = AlarmTimeCalculator.nextTriggerMillis(alarm(7, 0, days = "3"), now, subtractWakeWindow = false)
        val result = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = resultMs }
        assertEquals(Calendar.WEDNESDAY, result.get(Calendar.DAY_OF_WEEK))
        assertEquals(7, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, result.get(Calendar.MINUTE))
        assertTrue("result must be in the future", resultMs > now.timeInMillis)
        assertTrue("result must be within a week", resultMs - now.timeInMillis <= 7L * 24 * 3600 * 1000)
    }

    @Test
    fun `empty day set means every day - no weekday skipping`() {
        val now = cal("UTC", 2026, Calendar.JANUARY, 5, 8, 0)
        val result = AlarmTimeCalculator.nextTriggerMillis(alarm(7, 0, days = ""), now, subtractWakeWindow = false)
        // Simply the next occurrence (tomorrow), no multi-day weekday hunt.
        assertEquals(cal("UTC", 2026, Calendar.JANUARY, 6, 7, 0).timeInMillis, result)
    }

    @Test
    fun `spring-forward night loses an hour of real time but keeps wall-clock alarm`() {
        // US DST starts 2026-03-08 02:00 -> 03:00 (America/New_York).
        val now = cal("America/New_York", 2026, Calendar.MARCH, 7, 20, 0) // Sat 20:00
        val resultMs = AlarmTimeCalculator.nextTriggerMillis(alarm(7, 0), now, subtractWakeWindow = false)
        val result = Calendar.getInstance(TimeZone.getTimeZone("America/New_York")).apply { timeInMillis = resultMs }
        assertEquals(7, result.get(Calendar.HOUR_OF_DAY)) // wall clock preserved
        // Sat 20:00 -> Sun 07:00 would be 11h, but the clock skips 02:00-03:00, so 10h of real time.
        assertEquals(10L * 60 * 60 * 1000, resultMs - now.timeInMillis)
    }

    @Test
    fun `fall-back night gains an hour of real time but keeps wall-clock alarm`() {
        // US DST ends 2026-11-01 02:00 -> 01:00 (America/New_York).
        val now = cal("America/New_York", 2026, Calendar.OCTOBER, 31, 20, 0) // Sat 20:00
        val resultMs = AlarmTimeCalculator.nextTriggerMillis(alarm(7, 0), now, subtractWakeWindow = false)
        val result = Calendar.getInstance(TimeZone.getTimeZone("America/New_York")).apply { timeInMillis = resultMs }
        assertEquals(7, result.get(Calendar.HOUR_OF_DAY)) // wall clock preserved
        // Sat 20:00 -> Sun 07:00 would be 11h, but the clock repeats 01:00-02:00, so 12h of real time.
        assertEquals(12L * 60 * 60 * 1000, resultMs - now.timeInMillis)
    }

    @Test
    fun `parseDays trims spaces and drops invalid tokens`() {
        assertEquals(setOf(1, 2, 3), AlarmTimeCalculator.parseDays("1, 2 ,3"))
        assertEquals(emptySet<Int>(), AlarmTimeCalculator.parseDays(""))
        assertEquals(setOf(1, 3), AlarmTimeCalculator.parseDays("1,x,3"))
    }

    @Test
    fun `calendarDayToOurDay maps Monday-first`() {
        assertEquals(1, AlarmTimeCalculator.calendarDayToOurDay(Calendar.MONDAY))
        assertEquals(5, AlarmTimeCalculator.calendarDayToOurDay(Calendar.FRIDAY))
        assertEquals(6, AlarmTimeCalculator.calendarDayToOurDay(Calendar.SATURDAY))
        assertEquals(7, AlarmTimeCalculator.calendarDayToOurDay(Calendar.SUNDAY))
    }
}
