package tech.future.sleepanalyzer.wearables

import kotlin.math.roundToInt

/** A single human-readable health-context note surfaced on the Sleep Result screen (#7). */
data class HealthContextInsight(val label: String, val detail: String)

/**
 * Pure, testable layer that turns opt-in "Detailed health context" samples (#7) into conservative,
 * non-diagnostic notes for the Sleep Result screen.
 *
 * It deliberately does NOT alter the numeric sleep score. The relationships it surfaces
 * (caffeine → onset latency, overnight fluids → fragmentation, core temperature → sleep depth) are
 * real but individually variable, so the app presents observations the user can act on rather than
 * baking speculative deltas into the score. Keeping this logic pure makes each note unit-testable.
 */
object HealthContextInsights {

    /** Caffeine logged within this many hours before bedtime is treated as potentially onset-delaying. */
    const val LATE_CAFFEINE_HOURS = 6L
    private const val HOUR_MS = 3_600_000L

    /**
     * @param sessionStartMs bedtime (session start) in epoch millis
     * @param caffeineMg   (timestamp, milligrams) pairs
     * @param hydrationMl  (timestamp, milliliters) pairs
     * @param bodyTempC    (timestamp, degrees Celsius) pairs
     */
    fun build(
        sessionStartMs: Long,
        caffeineMg: List<Pair<Long, Float>> = emptyList(),
        hydrationMl: List<Pair<Long, Float>> = emptyList(),
        bodyTempC: List<Pair<Long, Float>> = emptyList()
    ): List<HealthContextInsight> {
        val insights = mutableListOf<HealthContextInsight>()

        val windowStart = sessionStartMs - LATE_CAFFEINE_HOURS * HOUR_MS
        val lateCaffeineTotal = caffeineMg
            .filter { it.first in windowStart until sessionStartMs }
            .sumOf { it.second.toDouble() }
        if (lateCaffeineTotal > 0.0) {
            insights += HealthContextInsight(
                label = "Caffeine before bed",
                detail = "${lateCaffeineTotal.roundToInt()} mg logged within ${LATE_CAFFEINE_HOURS}h of " +
                    "bedtime \u2014 caffeine can lengthen the time it takes to fall asleep."
            )
        }

        val hydrationTotal = hydrationMl.sumOf { it.second.toDouble() }
        if (hydrationTotal > 0.0) {
            insights += HealthContextInsight(
                label = "Fluids overnight",
                detail = "${hydrationTotal.roundToInt()} ml logged around this night \u2014 large amounts can " +
                    "fragment sleep with bathroom trips."
            )
        }

        if (bodyTempC.isNotEmpty()) {
            val avg = bodyTempC.map { it.second }.average()
            insights += HealthContextInsight(
                label = "Body temperature",
                detail = "Averaged ${"%.1f".format(avg)} \u00B0C \u2014 a cooler core supports deeper sleep."
            )
        }

        return insights
    }
}
