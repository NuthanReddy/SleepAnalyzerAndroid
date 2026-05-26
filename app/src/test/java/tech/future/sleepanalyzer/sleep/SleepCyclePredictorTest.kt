package tech.future.sleepanalyzer.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepCyclePredictorTest {

    @Test
    fun `cycle length varies with age band`() {
        assertEquals(80f, SleepCyclePredictor.cycleLengthMinutes(15), 0.001f)
        assertEquals(90f, SleepCyclePredictor.cycleLengthMinutes(25), 0.001f)
        assertEquals(92f, SleepCyclePredictor.cycleLengthMinutes(35), 0.001f)
        assertEquals(95f, SleepCyclePredictor.cycleLengthMinutes(60), 0.001f)
        assertEquals(100f, SleepCyclePredictor.cycleLengthMinutes(70), 0.001f)
    }

    @Test
    fun `null age uses 30 default`() {
        assertEquals(92f, SleepCyclePredictor.cycleLengthMinutes(null), 0.001f)
    }

    @Test
    fun `phase wraps and stays under 1`() {
        val phase = SleepCyclePredictor.phase(0L, 90L * 60 * 1000, ageYears = 25)
        assertTrue("phase $phase should be in [0,1)", phase >= 0f && phase < 1f)
    }

    @Test
    fun `phase advances monotonically inside a cycle`() {
        val phaseEarly = SleepCyclePredictor.phase(0L, 15L * 60 * 1000, 25)
        val phaseMid = SleepCyclePredictor.phase(0L, 45L * 60 * 1000, 25)
        val phaseLate = SleepCyclePredictor.phase(0L, 80L * 60 * 1000, 25)
        assertTrue(phaseEarly < phaseMid)
        assertTrue(phaseMid < phaseLate)
    }

    @Test
    fun `stage bias matches expected phase windows`() {
        assertEquals(SleepStage.LIGHT, SleepCyclePredictor.typicalStageBias(0.10f))
        assertEquals(SleepStage.DEEP, SleepCyclePredictor.typicalStageBias(0.35f))
        assertEquals(SleepStage.LIGHT, SleepCyclePredictor.typicalStageBias(0.65f))
        assertEquals(SleepStage.REM, SleepCyclePredictor.typicalStageBias(0.85f))
    }

    @Test
    fun `negative elapsed clamps to phase 0`() {
        val phase = SleepCyclePredictor.phase(1000L, 0L, 30)
        assertEquals(0f, phase, 0.0001f)
    }
}
