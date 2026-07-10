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
import kotlinx.coroutines.flow.update
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

data class RecordingSession(
    val key: Long,
    val title: String,
    val recordings: List<AudioRecording>
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

    private val sessionDateFormat = java.text.SimpleDateFormat("EEE, MMM d · h:mm a", java.util.Locale.getDefault())

    /** Recordings grouped into sessions (by sessionId, falling back to date), newest first. */
    val recordingSessions: StateFlow<List<RecordingSession>> = recentRecordings
        .map { list ->
            list.groupBy { it.sessionId ?: -it.date.hashCode().toLong() }
                .map { (key, items) ->
                    val sorted = items.sortedBy { it.startTime }
                    val header = sorted.firstOrNull()?.let { sessionDateFormat.format(java.util.Date(it.startTime)) } ?: ""
                    RecordingSession(key = key, title = header, recordings = sorted)
                }
                .sortedByDescending { it.recordings.firstOrNull()?.startTime ?: 0L }
        }
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

    /** Per-recording transcription progress/errors, keyed by recording id. */
    private val _transcriptionState = MutableStateFlow<Map<Long, TranscriptionUiState>>(emptyMap())
    val transcriptionState: StateFlow<Map<Long, TranscriptionUiState>> = _transcriptionState

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

    /**
     * Runs offline speech-to-text on a "talk" recording and persists the transcript. The Vosk model
     * downloads on first use (~40 MB), so this may take a while the very first time.
     */
    fun transcribe(recording: AudioRecording) {
        val id = recording.id
        if (_transcriptionState.value[id] == TranscriptionUiState.Running) return
        _transcriptionState.update { it + (id to TranscriptionUiState.Running) }
        viewModelScope.launch {
            try {
                val text = tech.future.sleepanalyzer.transcription.VoskTranscriber
                    .transcribe(getApplication(), recording.filePath)
                val stored = if (text.isBlank()) "" else text
                repository.updateRecordingTranscript(id, stored)
                _transcriptionState.update { it + (id to TranscriptionUiState.Done) }
            } catch (t: Throwable) {
                _transcriptionState.update {
                    it + (id to TranscriptionUiState.Error(t.message ?: "Transcription failed"))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}

sealed interface TranscriptionUiState {
    data object Running : TranscriptionUiState
    data object Done : TranscriptionUiState
    data class Error(val message: String) : TranscriptionUiState
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
