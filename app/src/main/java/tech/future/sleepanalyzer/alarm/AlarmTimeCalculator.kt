package tech.future.sleepanalyzer.alarm

import tech.future.sleepanalyzer.data.db.entity.AlarmConfig
import java.util.Calendar

/**
 * Pure, clock-injectable calculation of an alarm's next fire time.
 *
 * Extracted from [AlarmScheduler] so the day-of-week selection, next-occurrence rollover,
 * wake-window subtraction and DST handling can be unit-tested without AlarmManager or the
 * system clock. Keeping this a plain object (no Android types) means it runs on the JVM in a
 * fast local unit test.
 */
object AlarmTimeCalculator {

    /**
     * The next epoch-millis at which [alarm] should trigger, at or after [now].
     *
     * The returned instant preserves [now]'s time zone, so [Calendar] arithmetic here respects
     * DST transitions (a wall-clock 07:00 alarm stays 07:00 across a spring-forward / fall-back).
     *
     * @param now the reference "current time" (its time zone is used for all calculations).
     * @param subtractWakeWindow when true, returns the smart-wake window START
     *   (deadline minus [AlarmConfig.wakeWindowMinutes]); when false, the deadline itself.
     */
    fun nextTriggerMillis(
        alarm: AlarmConfig,
        now: Calendar,
        subtractWakeWindow: Boolean
    ): Long {
        val alarmTime = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val enabledDays = parseDays(alarm.daysOfWeek)
        while (alarmTime.before(now) ||
            (enabledDays.isNotEmpty() && calendarDayToOurDay(alarmTime.get(Calendar.DAY_OF_WEEK)) !in enabledDays)
        ) {
            alarmTime.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (subtractWakeWindow) {
            alarmTime.add(Calendar.MINUTE, -alarm.wakeWindowMinutes)
        }
        return alarmTime.timeInMillis
    }

    /** Parse the comma-separated `daysOfWeek` field into our 1=Mon..7=Sun day numbers. */
    fun parseDays(daysOfWeek: String): Set<Int> =
        daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    /** Map [Calendar]'s 1=Sun..7=Sat day-of-week to our 1=Mon..7=Sun convention. */
    fun calendarDayToOurDay(calendarDay: Int): Int = when (calendarDay) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }
}
