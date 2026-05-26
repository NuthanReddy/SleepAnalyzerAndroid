package tech.future.sleepanalyzer.ui.tracker

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.service.SleepTrackingService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SleepTrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SleepRepository(application)

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId

    private val _trackingStartTime = MutableStateFlow<Long?>(null)
    val trackingStartTime: StateFlow<Long?> = _trackingStartTime

    private val _moodBefore = MutableStateFlow<String?>(null)
    val moodBefore: StateFlow<String?> = _moodBefore

    private val _moodAfter = MutableStateFlow<String?>(null)
    val moodAfter: StateFlow<String?> = _moodAfter

    private val _showMoodSelector = MutableStateFlow(false)
    val showMoodSelector: StateFlow<Boolean> = _showMoodSelector

    private val _isMoodBeforeSelection = MutableStateFlow(true)
    val isMoodBeforeSelection: StateFlow<Boolean> = _isMoodBeforeSelection

    private val _pendingStopCompletion = MutableStateFlow(false)
    val pendingStopCompletion: StateFlow<Boolean> = _pendingStopCompletion

    private val stopCompletedChannel = Channel<Long>(Channel.BUFFERED)
    val stopCompletedEvents = stopCompletedChannel.receiveAsFlow()

    val recentSessions: StateFlow<List<SleepSession>> = repository.getRecentSessions(7)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val active = repository.getActiveSession()
            if (active != null) {
                _isTracking.value = true
                _currentSessionId.value = active.id
                _trackingStartTime.value = active.startTime
            }
        }
    }

    fun showMoodBefore() {
        _isMoodBeforeSelection.value = true
        _showMoodSelector.value = true
    }

    fun requestStop() {
        _isMoodBeforeSelection.value = false
        _showMoodSelector.value = true
        _pendingStopCompletion.value = true
    }

    fun selectMood(mood: String) {
        if (_isMoodBeforeSelection.value) {
            _moodBefore.value = mood
        } else {
            _moodAfter.value = mood
        }
        _showMoodSelector.value = false
        completePendingStopIfNeeded()
    }

    fun dismissMoodSelector() {
        _showMoodSelector.value = false
        completePendingStopIfNeeded()
    }

    fun startTracking() {
        viewModelScope.launch {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val session = SleepSession(
                startTime = System.currentTimeMillis(),
                isTracking = true,
                date = dateFormat.format(Date()),
                moodBefore = _moodBefore.value
            )
            val id = repository.insertSession(session)
            _currentSessionId.value = id
            _trackingStartTime.value = session.startTime
            _isTracking.value = true
            _pendingStopCompletion.value = false

            val app = getApplication<Application>()
            val intent = Intent(app, SleepTrackingService::class.java).apply {
                action = SleepTrackingService.ACTION_START
                putExtra(SleepTrackingService.EXTRA_SESSION_ID, id)
            }
            app.startForegroundService(intent)
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            stopTrackingInternal()
        }
    }

    private fun completePendingStopIfNeeded() {
        if (!_pendingStopCompletion.value) return
        _pendingStopCompletion.value = false

        viewModelScope.launch {
            val sessionId = stopTrackingInternal()
            sessionId?.let { stopCompletedChannel.send(it) }
        }
    }

    private suspend fun stopTrackingInternal(): Long? {
        val sessionId = _currentSessionId.value

        sessionId?.let { id ->
            val session = repository.getSessionById(id)
            session?.let {
                repository.updateSession(it.copy(moodAfter = _moodAfter.value))
            }
        }

        val app = getApplication<Application>()
        val intent = Intent(app, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_STOP
        }
        app.startService(intent)

        _isTracking.value = false
        _pendingStopCompletion.value = false
        _showMoodSelector.value = false
        _moodBefore.value = null
        _moodAfter.value = null
        return sessionId
    }
}
