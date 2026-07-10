package tech.future.sleepanalyzer.wearables

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthContextInsightsTest {

    private val bedtime = 1_000_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun `no samples yields no insights`() {
        assertTrue(HealthContextInsights.build(bedtime).isEmpty())
    }

    @Test
    fun `caffeine within the pre-bed window is flagged and summed`() {
        val result = HealthContextInsights.build(
            sessionStartMs = bedtime,
            caffeineMg = listOf(
                bedtime - 2 * hour to 80f,
                bedtime - 4 * hour to 40f
            )
        )
        val caffeine = result.single { it.label == "Caffeine before bed" }
        assertTrue(caffeine.detail.contains("120 mg"))
    }

    @Test
    fun `caffeine older than the window is ignored`() {
        val result = HealthContextInsights.build(
            sessionStartMs = bedtime,
            caffeineMg = listOf(bedtime - (HealthContextInsights.LATE_CAFFEINE_HOURS + 1) * hour to 200f)
        )
        assertTrue(result.none { it.label == "Caffeine before bed" })
    }

    @Test
    fun `caffeine exactly at window start is included and at bedtime is excluded`() {
        val windowStart = bedtime - HealthContextInsights.LATE_CAFFEINE_HOURS * hour
        val included = HealthContextInsights.build(
            sessionStartMs = bedtime,
            caffeineMg = listOf(windowStart to 50f)
        )
        assertTrue(included.any { it.label == "Caffeine before bed" })

        val excluded = HealthContextInsights.build(
            sessionStartMs = bedtime,
            caffeineMg = listOf(bedtime to 50f)
        )
        assertTrue(excluded.none { it.label == "Caffeine before bed" })
    }

    @Test
    fun `hydration totals are reported`() {
        val result = HealthContextInsights.build(
            sessionStartMs = bedtime,
            hydrationMl = listOf(bedtime + hour to 250f, bedtime + 2 * hour to 300f)
        )
        val hydration = result.single { it.label == "Fluids overnight" }
        assertTrue(hydration.detail.contains("550 ml"))
    }

    @Test
    fun `body temperature reports an average`() {
        val result = HealthContextInsights.build(
            sessionStartMs = bedtime,
            bodyTempC = listOf(bedtime to 36.4f, bedtime + hour to 36.6f)
        )
        val temp = result.single { it.label == "Body temperature" }
        assertTrue(temp.detail.contains("36.5"))
    }

    @Test
    fun `multiple metrics produce multiple insights`() {
        val result = HealthContextInsights.build(
            sessionStartMs = bedtime,
            caffeineMg = listOf(bedtime - hour to 60f),
            hydrationMl = listOf(bedtime + hour to 200f),
            bodyTempC = listOf(bedtime to 36.5f)
        )
        assertEquals(3, result.size)
    }
}
