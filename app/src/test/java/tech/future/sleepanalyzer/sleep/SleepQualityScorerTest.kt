package tech.future.sleepanalyzer.sleep

import org.junit.Assert.assertTrue
import org.junit.Test
import tech.future.sleepanalyzer.data.db.entity.UserProfile

class SleepQualityScorerTest {

    @Test
    fun `perfect 8 hour sleep with no interruptions scores high`() {
        val score = SleepQualityScorer.calculate(
            durationMinutes = 480,
            interruptions = 0,
            deepSleepMinutes = 120,
            lightSleepMinutes = 240,
            remSleepMinutes = 120,
            profile = null
        )
        assertTrue("expected >= 80, got $score", score >= 80)
    }

    @Test
    fun `very short sleep scores lower than full`() {
        val short = SleepQualityScorer.calculate(180, 0, 40, 80, 60, null)
        val full = SleepQualityScorer.calculate(480, 0, 120, 240, 120, null)
        assertTrue("short ($short) should be lower than full ($full)", short < full)
    }

    @Test
    fun `interruptions reduce score`() {
        val none = SleepQualityScorer.calculate(480, 0, 120, 240, 120, null)
        val many = SleepQualityScorer.calculate(480, 6, 120, 240, 120, null)
        assertTrue("interrupted ($many) should be lower than none ($none)", many < none)
    }

    @Test
    fun `score stays within 1 to 100`() {
        val empty = SleepQualityScorer.calculate(0, 100, 0, 0, 0, null)
        assertTrue("empty score $empty out of range", empty in 1..100)
        val huge = SleepQualityScorer.calculate(720, 0, 720, 0, 0, null)
        assertTrue("huge score $huge out of range", huge in 1..100)
    }

    @Test
    fun `athlete penalised more for low deep ratio than sedentary`() {
        val sedentary = UserProfile(id = 1, activityLevel = "sedentary")
        val athlete = UserProfile(id = 1, activityLevel = "athlete")
        // 10% deep ratio -- well below athlete target (30%) but closer to sedentary target (18%)
        val athleteScore = SleepQualityScorer.calculate(480, 0, 48, 312, 120, athlete)
        val sedentaryScore = SleepQualityScorer.calculate(480, 0, 48, 312, 120, sedentary)
        assertTrue(
            "athlete ($athleteScore) should score <= sedentary ($sedentaryScore) for same low deep ratio",
            athleteScore <= sedentaryScore
        )
    }

    @Test
    fun `score never throws on degenerate input`() {
        SleepQualityScorer.calculate(-100, -5, -10, -20, -30, null)
        SleepQualityScorer.calculate(Int.MAX_VALUE, 0, Int.MAX_VALUE, 0, 0, null)
    }
}
