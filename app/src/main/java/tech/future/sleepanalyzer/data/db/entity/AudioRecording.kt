package tech.future.sleepanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_recordings")
data class AudioRecording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long? = null,
    val filePath: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int = 0,
    val type: String = "unknown", // "snore", "cough", "talk", "noise", "unknown"
    val maxAmplitude: Int = 0,
    val date: String = "", // YYYY-MM-DD
    // New: attribution and confidence (when voice isolation is enabled)
    val attributedTo: String = "unknown", // "user", "partner", "unknown"
    val matchConfidence: Float = 0f, // 0..1
    val pitchHz: Float = 0f,
    val croppedFromMs: Int = 0, // start offset within the raw capture buffer
    val croppedToMs: Int = 0,   // end offset within the raw capture buffer
    // Offline speech-to-text of "talk" events. null = not yet attempted, "" = attempted but empty.
    val transcript: String? = null
)
