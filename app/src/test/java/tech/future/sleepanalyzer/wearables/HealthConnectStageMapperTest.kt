package tech.future.sleepanalyzer.wearables

import androidx.health.connect.client.records.SleepSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tech.future.sleepanalyzer.sleep.SleepStage

class HealthConnectStageMapperTest {

    @Test
    fun `awake and awake-in-bed map to AWAKE`() {
        assertEquals(SleepStage.AWAKE, HealthConnectStageMapper.map(SleepSessionRecord.STAGE_TYPE_AWAKE))
        assertEquals(SleepStage.AWAKE, HealthConnectStageMapper.map(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED))
    }

    @Test
    fun `light, generic sleeping and unknown collapse to LIGHT`() {
        assertEquals(SleepStage.LIGHT, HealthConnectStageMapper.map(SleepSessionRecord.STAGE_TYPE_LIGHT))
        assertEquals(SleepStage.LIGHT, HealthConnectStageMapper.map(SleepSessionRecord.STAGE_TYPE_SLEEPING))
        assertEquals(SleepStage.LIGHT, HealthConnectStageMapper.map(SleepSessionRecord.STAGE_TYPE_UNKNOWN))
    }

    @Test
    fun `deep and rem map through directly`() {
        assertEquals(SleepStage.DEEP, HealthConnectStageMapper.map(SleepSessionRecord.STAGE_TYPE_DEEP))
        assertEquals(SleepStage.REM, HealthConnectStageMapper.map(SleepSessionRecord.STAGE_TYPE_REM))
    }

    @Test
    fun `out-of-bed is dropped`() {
        assertNull(HealthConnectStageMapper.map(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED))
    }

    @Test
    fun `unrecognized stage type defaults to LIGHT`() {
        assertEquals(SleepStage.LIGHT, HealthConnectStageMapper.map(Int.MAX_VALUE))
    }
}
