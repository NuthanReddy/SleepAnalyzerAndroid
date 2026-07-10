package tech.future.sleepanalyzer.wearables

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.future.sleepanalyzer.data.db.entity.WearableDevice
import tech.future.sleepanalyzer.data.db.entity.WearableSample

class WearableSyncManagerTest {

    private fun sample(
        deviceId: String? = null,
        sessionId: Long? = null,
        timestamp: Long = 1_000L,
        metric: WearableMetric = WearableMetric.HEART_RATE,
        value: Float = 60f
    ) = WearableSample(
        sessionId = sessionId,
        timestamp = timestamp,
        metric = metric.name,
        value = value,
        unit = metric.unit,
        deviceId = deviceId,
        sourceProvider = "fake"
    )

    private fun device(id: String, name: String = "Device") =
        WearableDevice(id = id, displayName = name, sourceProvider = "fake")

    private class FakeSource(
        override val id: String = "fake",
        override val displayName: String = "Fake",
        private val hasPerms: Boolean = true,
        private val devices: List<WearableDevice> = emptyList(),
        private val samples: List<WearableSample> = emptyList(),
        private val throwOnPermissions: Boolean = false,
        private val throwOnListDevices: Boolean = false,
        private val throwOnSync: Boolean = false
    ) : WearableSource {
        override suspend fun isAvailable() = true
        override suspend fun requiredPermissions() = emptySet<String>()
        override suspend fun hasPermissions(): Boolean {
            if (throwOnPermissions) error("perm boom")
            return hasPerms
        }
        override suspend fun listDevices(): List<WearableDevice> {
            if (throwOnListDevices) error("list boom")
            return devices
        }
        override suspend fun syncSince(
            sinceMs: Long,
            untilMs: Long,
            metrics: Set<WearableMetric>
        ): List<WearableSample> {
            if (throwOnSync) error("sync boom")
            return samples
        }
    }

    private fun collect(src: WearableSource, sessionId: Long? = null) = runBlocking {
        WearableSyncManager.collectFromSource(
            src, startMs = 0L, endMs = 10_000L,
            metrics = WearableSyncManager.DEFAULT_METRICS, sessionId = sessionId
        )
    }

    @Test
    fun `no permission yields empty result`() {
        val result = collect(FakeSource(hasPerms = false, samples = listOf(sample())))
        assertTrue(result.devices.isEmpty())
        assertTrue(result.samples.isEmpty())
    }

    @Test
    fun `permission check that throws is treated as no permission`() {
        val result = collect(FakeSource(throwOnPermissions = true, samples = listOf(sample())))
        assertTrue(result.samples.isEmpty())
    }

    @Test
    fun `sync failure degrades to empty samples but keeps listed devices`() {
        val result = collect(
            FakeSource(devices = listOf(device("fake:d1")), throwOnSync = true)
        )
        assertEquals(listOf("fake:d1"), result.devices.map { it.id })
        assertTrue(result.samples.isEmpty())
    }

    @Test
    fun `listDevices failure still returns samples`() {
        val result = collect(
            FakeSource(throwOnListDevices = true, samples = listOf(sample()))
        )
        assertEquals(1, result.samples.size)
    }

    @Test
    fun `stamps sessionId only when sample has none`() {
        val result = collect(
            FakeSource(samples = listOf(sample(sessionId = null), sample(sessionId = 7L, timestamp = 2_000L))),
            sessionId = 42L
        )
        assertEquals(listOf(42L, 7L), result.samples.map { it.sessionId })
    }

    @Test
    fun `infers a device from sample metadata when not listed`() {
        val result = collect(
            FakeSource(samples = listOf(sample(deviceId = "health_connect:com.fitbit.FitbitMobile")))
        )
        assertEquals(1, result.devices.size)
        assertEquals("health_connect:com.fitbit.FitbitMobile", result.devices.single().id)
        assertEquals("Fitbit Mobile", result.devices.single().displayName)
    }

    @Test
    fun `does not infer a device that is already listed`() {
        val result = collect(
            FakeSource(
                devices = listOf(device("fake:known", name = "Known Watch")),
                samples = listOf(sample(deviceId = "fake:known"))
            )
        )
        assertEquals(1, result.devices.size)
        assertEquals("Known Watch", result.devices.single().displayName)
    }

    @Test
    fun `inferred name falls back to source display name for default device`() {
        val result = collect(
            FakeSource(displayName = "Health Connect", samples = listOf(sample(deviceId = "health_connect:default")))
        )
        assertEquals("Health Connect", result.devices.single().displayName)
    }

    @Test
    fun `merge dedupes devices by id and flattens samples`() {
        val a = WearableSyncManager.SyncedSource(
            devices = listOf(device("fake:d1"), device("fake:d2")),
            samples = listOf(sample(timestamp = 1L))
        )
        val b = WearableSyncManager.SyncedSource(
            devices = listOf(device("fake:d2"), device("fake:d3")),
            samples = listOf(sample(timestamp = 2L), sample(timestamp = 3L))
        )
        val merged = WearableSyncManager.mergeSyncedSources(listOf(a, b))
        assertEquals(setOf("fake:d1", "fake:d2", "fake:d3"), merged.devices.map { it.id }.toSet())
        assertEquals(3, merged.devices.size)
        assertEquals(3, merged.samples.size)
    }

    @Test
    fun `merge of empty source list is empty`() {
        val merged = WearableSyncManager.mergeSyncedSources(emptyList())
        assertTrue(merged.devices.isEmpty())
        assertTrue(merged.samples.isEmpty())
    }
}
