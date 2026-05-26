package tech.future.sleepanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores an enrolled voice profile used to attribute snore/talk events to a known user.
 * Derived features come from a few seconds of microphone audio captured during setup.
 */
@Entity(tableName = "voice_profiles")
data class VoiceProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String = "me",
    val sampleFilePath: String? = null,
    val pitchMeanHz: Float = 0f,
    val pitchStdHz: Float = 0f,
    val spectralCentroidMean: Float = 0f,
    val spectralCentroidStd: Float = 0f,
    val zeroCrossingRate: Float = 0f,
    val rmsMean: Float = 0f,
    // Pipe-separated 13 MFCC-like band energy means
    val bandEnergyMeans: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
