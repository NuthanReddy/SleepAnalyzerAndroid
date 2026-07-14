package tech.future.sleepanalyzer.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tech.future.sleepanalyzer.data.db.entity.AudioRecording
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.prefs.AppPreferences
import tech.future.sleepanalyzer.data.repository.SleepRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalBackupManagerTest {

    @Test
    fun portableArchiveRestoresDatabasePreferencesAndAudio() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SleepRepository(context)
        val preferences = AppPreferences(context)
        val manager = LocalBackupManager(context, repository, preferences)
        val id = System.currentTimeMillis()
        val session = SleepSession(
            id = id,
            startTime = id,
            endTime = id + 60_000L,
            durationMinutes = 1,
            date = "2099-01-01"
        )
        val recordingBytes = byteArrayOf(1, 2, 3, 4, 5, 6)
        val originalFile = File(context.filesDir, "recordings/test_$id.wav").apply {
            parentFile?.mkdirs()
            writeBytes(recordingBytes)
        }
        val recording = AudioRecording(
            id = id,
            sessionId = id,
            filePath = originalFile.absolutePath,
            startTime = id,
            endTime = id + 1_000L,
            durationSeconds = 1,
            type = "cough",
            date = "2099-01-01"
        )
        val originalWeeklyReport = preferences.weeklyReportEnabledFlow.first()

        try {
            repository.insertSession(session)
            repository.insertRecording(recording)

            val output = ByteArrayOutputStream()
            val exported = manager.exportTo(output).getOrThrow()
            assertTrue(exported.databaseRecords >= 2)
            assertTrue(exported.mediaFiles >= 1)

            repository.deleteRecording(recording)
            repository.deleteSession(session)
            originalFile.delete()
            preferences.setWeeklyReportEnabled(!originalWeeklyReport)

            val restored = manager.importFrom(ByteArrayInputStream(output.toByteArray())).getOrThrow()

            assertTrue(restored.databaseRecords >= 2)
            assertNotNull(repository.getSessionById(id))
            val restoredRecording = repository.getAllRecordings().first().first { it.id == id }
            val restoredFile = File(restoredRecording.filePath)
            assertTrue(restoredFile.isFile)
            assertArrayEquals(recordingBytes, restoredFile.readBytes())
            assertEquals(originalWeeklyReport, preferences.weeklyReportEnabledFlow.first())
        } finally {
            repository.getAllRecordings().first()
                .firstOrNull { it.id == id }
                ?.let {
                    File(it.filePath).delete()
                    repository.deleteRecording(it)
                }
            repository.getSessionById(id)?.let { repository.deleteSession(it) }
            preferences.setWeeklyReportEnabled(originalWeeklyReport)
        }
    }
}
