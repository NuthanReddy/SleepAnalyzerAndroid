package tech.future.sleepanalyzer.wearables

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import tech.future.sleepanalyzer.data.db.entity.WearableDevice
import tech.future.sleepanalyzer.data.db.entity.WearableSample
import tech.future.sleepanalyzer.data.db.entity.WearableSleepStage
import tech.future.sleepanalyzer.sleep.SleepStage
import java.time.Instant

/**
 * Reads health data from the Health Connect app. This single source covers most popular wearables
 * (Fitbit, Samsung, Garmin, Oura, Pixel Watch, ...) because they all write through Health Connect now.
 *
 * Permissions are requested by the UI via ActivityResultContracts.RequestMultiplePermissions
 * (with PermissionController.createRequestPermissionResultContract());
 * this class only reports the set of permissions needed.
 *
 * Note: SkinTemperatureRecord is not consumed yet because it is gated behind a newer
 * Health Connect SDK release than the one this app currently pins. The factory map keeps the
 * metric enum future-proof so the integration just needs to add the read call later.
 */
class HealthConnectSource(private val context: Context) : WearableSource {

    override val id: String = "health_connect"
    override val displayName: String = "Health Connect"

    private val provider: String get() = id

    private val client: HealthConnectClient? by lazy {
        try { HealthConnectClient.getOrCreate(context) } catch (_: Throwable) { null }
    }

    override suspend fun isAvailable(): Boolean {
        val status = try { HealthConnectClient.getSdkStatus(context) } catch (_: Throwable) { return false }
        return status == HealthConnectClient.SDK_AVAILABLE
    }

    override suspend fun requiredPermissions(): Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class)
    )

    override suspend fun hasPermissions(): Boolean {
        val c = client ?: return false
        val granted = try { c.permissionController.getGrantedPermissions() } catch (_: Throwable) { return false }
        return granted.containsAll(requiredPermissions())
    }

    override suspend fun listDevices(): List<WearableDevice> {
        // Health Connect doesn't expose a device list directly; surface a single virtual device
        // and let the integration layer split by per-record metadata if needed.
        return if (isAvailable()) {
            listOf(
                WearableDevice(
                    id = "$provider:default",
                    displayName = displayName,
                    type = "unknown",
                    sourceProvider = provider,
                    capabilities = SUPPORTED_METRICS.joinToString(",") { it.name }
                )
            )
        } else emptyList()
    }

    override suspend fun syncSince(
        sinceMs: Long,
        untilMs: Long,
        metrics: Set<WearableMetric>
    ): List<WearableSample> {
        val c = client ?: return emptyList()
        if (!hasPermissions()) return emptyList()
        val range = timeRange(sinceMs, untilMs)
        val out = mutableListOf<WearableSample>()

        if (WearableMetric.HEART_RATE in metrics) {
            runCatching {
                val resp = c.readRecords(ReadRecordsRequest(HeartRateRecord::class, range))
                resp.records.forEach { record ->
                    record.samples.forEach { sample ->
                        out += WearableSample(
                            timestamp = sample.time.toEpochMilli(),
                            metric = WearableMetric.HEART_RATE.name,
                            value = sample.beatsPerMinute.toFloat(),
                            unit = WearableMetric.HEART_RATE.unit,
                            deviceId = deviceIdOf(record.metadata),
                            sourceProvider = provider
                        )
                    }
                }
            }
        }
        if (WearableMetric.RESTING_HEART_RATE in metrics) {
            runCatching {
                val resp = c.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range))
                resp.records.forEach { r ->
                    out += WearableSample(
                        timestamp = r.time.toEpochMilli(),
                        metric = WearableMetric.RESTING_HEART_RATE.name,
                        value = r.beatsPerMinute.toFloat(),
                        unit = WearableMetric.RESTING_HEART_RATE.unit,
                        deviceId = deviceIdOf(r.metadata),
                        sourceProvider = provider
                    )
                }
            }
        }
        if (WearableMetric.HRV_RMSSD in metrics) {
            runCatching {
                val resp = c.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, range))
                resp.records.forEach { r ->
                    out += WearableSample(
                        timestamp = r.time.toEpochMilli(),
                        metric = WearableMetric.HRV_RMSSD.name,
                        value = r.heartRateVariabilityMillis.toFloat(),
                        unit = WearableMetric.HRV_RMSSD.unit,
                        deviceId = deviceIdOf(r.metadata),
                        sourceProvider = provider
                    )
                }
            }
        }
        if (WearableMetric.RESPIRATORY_RATE in metrics) {
            runCatching {
                val resp = c.readRecords(ReadRecordsRequest(RespiratoryRateRecord::class, range))
                resp.records.forEach { r ->
                    out += WearableSample(
                        timestamp = r.time.toEpochMilli(),
                        metric = WearableMetric.RESPIRATORY_RATE.name,
                        value = r.rate.toFloat(),
                        unit = WearableMetric.RESPIRATORY_RATE.unit,
                        deviceId = deviceIdOf(r.metadata),
                        sourceProvider = provider
                    )
                }
            }
        }
        if (WearableMetric.SPO2 in metrics) {
            runCatching {
                val resp = c.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, range))
                resp.records.forEach { r ->
                    out += WearableSample(
                        timestamp = r.time.toEpochMilli(),
                        metric = WearableMetric.SPO2.name,
                        value = r.percentage.value.toFloat(),
                        unit = WearableMetric.SPO2.unit,
                        deviceId = deviceIdOf(r.metadata),
                        sourceProvider = provider
                    )
                }
            }
        }
        if (WearableMetric.STEPS in metrics) {
            runCatching {
                val resp = c.readRecords(ReadRecordsRequest(StepsRecord::class, range))
                resp.records.forEach { r ->
                    out += WearableSample(
                        timestamp = r.endTime.toEpochMilli(),
                        metric = WearableMetric.STEPS.name,
                        value = r.count.toFloat(),
                        unit = WearableMetric.STEPS.unit,
                        deviceId = deviceIdOf(r.metadata),
                        sourceProvider = provider
                    )
                }
            }
        }
        return out
    }

    suspend fun readLatestHeightCm(): Float? = runCatching {
        val c = client ?: return@runCatching null
        if (!hasPermissions()) return@runCatching null
        val now = Instant.now()
        val response = c.readRecords(
            ReadRecordsRequest(
                HeightRecord::class,
                TimeRangeFilter.between(now.minus(365, java.time.temporal.ChronoUnit.DAYS), now)
            )
        )
        response.records.maxByOrNull { it.time }?.height?.inMeters?.toFloat()?.let { it * 100f }
    }.getOrNull()

    suspend fun readLatestWeightKg(): Float? = runCatching {
        val c = client ?: return@runCatching null
        if (!hasPermissions()) return@runCatching null
        val now = Instant.now()
        val response = c.readRecords(
            ReadRecordsRequest(
                WeightRecord::class,
                TimeRangeFilter.between(now.minus(365, java.time.temporal.ChronoUnit.DAYS), now)
            )
        )
        response.records.maxByOrNull { it.time }?.weight?.inKilograms?.toFloat()
    }.getOrNull()

    suspend fun syncSleepSessions(sinceMs: Long, untilMs: Long): List<WearableSleepStage> = runCatching {
        val c = client ?: return@runCatching emptyList()
        if (!hasPermissions()) return@runCatching emptyList()

        val response = c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRange(sinceMs, untilMs)))
        response.records.flatMap { record ->
            val deviceId = "health_connect:${record.metadata.dataOrigin.packageName}"
            record.stages.orEmpty().mapNotNull { stage ->
                val mappedStage = mapSleepStage(stage.stage) ?: return@mapNotNull null
                val startMs = stage.startTime.toEpochMilli()
                val endMs = stage.endTime.toEpochMilli()
                if (endMs <= startMs) return@mapNotNull null
                WearableSleepStage(
                    startTime = startMs,
                    endTime = endMs,
                    stage = mappedStage.key,
                    sourceProvider = provider,
                    deviceId = deviceId
                )
            }
        }
    }.getOrElse { emptyList() }

    private fun timeRange(sinceMs: Long, untilMs: Long): TimeRangeFilter = TimeRangeFilter.between(
        Instant.ofEpochMilli(sinceMs.coerceAtLeast(0)),
        Instant.ofEpochMilli(untilMs.coerceAtLeast(sinceMs + 1))
    )

    private fun mapSleepStage(stageType: Int): SleepStage? = when (stageType) {
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

    private fun deviceIdOf(metadata: Metadata): String {
        val pkg = metadata.dataOrigin.packageName
        return "$provider:$pkg"
    }

    companion object {
        private val SUPPORTED_METRICS = listOf(
            WearableMetric.HEART_RATE,
            WearableMetric.RESTING_HEART_RATE,
            WearableMetric.HRV_RMSSD,
            WearableMetric.RESPIRATORY_RATE,
            WearableMetric.SPO2,
            WearableMetric.STEPS
        )
    }
}
