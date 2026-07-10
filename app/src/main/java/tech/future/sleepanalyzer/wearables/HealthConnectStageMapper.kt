package tech.future.sleepanalyzer.wearables

import androidx.health.connect.client.records.SleepSessionRecord
import tech.future.sleepanalyzer.sleep.SleepStage

/**
 * Pure mapping from Health Connect sleep-stage type ints to the app's [SleepStage] model.
 *
 * Extracted from [HealthConnectSource] so vendor-stage ingestion can be unit-tested without a
 * Health Connect client. Returns null for stages we intentionally drop (out-of-bed), collapses
 * generic "sleeping"/"unknown" to LIGHT, and defaults anything unrecognized to LIGHT so a future
 * Health Connect stage type never silently disappears.
 */
object HealthConnectStageMapper {

    fun map(stageType: Int): SleepStage? = when (stageType) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStage.AWAKE

        SleepSessionRecord.STAGE_TYPE_SLEEPING,
        SleepSessionRecord.STAGE_TYPE_LIGHT,
        SleepSessionRecord.STAGE_TYPE_UNKNOWN -> SleepStage.LIGHT

        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> null
        else -> SleepStage.LIGHT
    }
}
