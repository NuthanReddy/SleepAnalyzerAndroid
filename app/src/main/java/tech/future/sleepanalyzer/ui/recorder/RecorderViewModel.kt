package tech.future.sleepanalyzer.ui.recorder

import android.app.Application
import android.content.Intent
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.audio.Attribution
import tech.future.sleepanalyzer.audio.AudioEventType
import tech.future.sleepanalyzer.data.db.entity.AudioRecording
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.di.ServiceLocator
import tech.future.sleepanalyzer.service.AudioRecorderService
import java.io.File

data class RecordingAttributionStats(
    val totalCount: Int = 0,
    val userCount: Int = 0,
    val partnerCount: Int = 0,
    val unknownCount: Int = 0
)

class RecorderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: SleepRepository = ServiceLocator.run {
        initialize(application)
        repository
    }
    private val preferences = ServiceLocator.preferences
    private val sharing = SharingStarted.WhileSubscribed(5000)

    private val _isRecording = MutableStateFlow(AudioRecorderService.isActive)
    val isRecording: StateFlow<Boolean> = _isRecording

    val recentRecordings: StateFlow<List<AudioRecording>> = repository.getRecentRecordings(50)
        .stateIn(viewModelScope, sharing, emptyList())

    val voiceIsolationEnabled = preferences.voiceIsolationEnabledFlow
        .stateIn(viewModelScope, sharing, false)

    val snoreStats: StateFlow<RecordingAttributionStats> = recentRecordings
        .map { it.attributionStatsFor(AudioEventType.SNORE) }
        .stateIn(viewModelScope, sharing, RecordingAttributionStats())

    val coughStats: StateFlow<RecordingAttributionStats> = recentRecordings
        .map { it.attributionStatsFor(AudioEventType.COUGH) }
        .stateIn(viewModelScope, sharing, RecordingAttributionStats())

    val talkStats: StateFlow<RecordingAttributionStats> = recentRecordings
        .map { it.attributionStatsFor(AudioEventType.TALK) }
        .stateIn(viewModelScope, sharing, RecordingAttributionStats())

    val noiseCount: StateFlow<Int> = recentRecordings
        .map { list -> list.count { it.type == AudioEventType.NOISE.key } }
        .stateIn(viewModelScope, sharing, 0)

    private val _playingRecordingId = MutableStateFlow<Long?>(null)
    val playingRecordingId: StateFlow<Long?> = _playingRecordingId

    private var mediaPlayer: MediaPlayer? = null

    fun startRecording() {
        val app = getApplication<Application>()
        val intent = Intent(app, AudioRecorderService::class.java).apply {
            action = AudioRecorderService.ACTION_START
        }
        app.startForegroundService(intent)
        _isRecording.value = true
    }

    fun stopRecording() {
        val app = getApplication<Application>()
        val intent = Intent(app, AudioRecorderService::class.java).apply {
            action = AudioRecorderService.ACTION_STOP
        }
        app.startService(intent)
        _isRecording.value = false
    }

    fun playRecording(recording: AudioRecording) {
        stopPlayback()
        if (!File(recording.filePath).exists()) return

        mediaPlayer = MediaPlayer().apply {
            setDataSource(recording.filePath)
            prepare()
            start()
            setOnCompletionListener {
                _playingRecordingId.value = null
            }
        }
        _playingRecordingId.value = recording.id
    }

    fun stopPlayback() {
        val player = mediaPlayer
        try {
            player?.stop()
        } catch (_: IllegalStateException) {
            // MediaPlayer can throw if completion/release races with an explicit stop.
        } finally {
            player?.release()
        }
        mediaPlayer = null
        _playingRecordingId.value = null
    }

    fun deleteRecording(recording: AudioRecording) {
        viewModelScope.launch {
            File(recording.filePath).delete()
            repository.deleteRecording(recording)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}

private fun List<AudioRecording>.attributionStatsFor(type: AudioEventType): RecordingAttributionStats {
    val matches = filter { it.type == type.key }
    return RecordingAttributionStats(
        totalCount = matches.size,
        userCount = matches.count { it.attributedTo == Attribution.USER.key },
        partnerCount = matches.count { it.attributedTo == Attribution.PARTNER.key },
        unknownCount = matches.count { it.attributedTo == Attribution.UNKNOWN.key }
    )
}
