package tech.future.sleepanalyzer.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.repository.SleepRepository

class SessionsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SleepRepository(application)

    /** All completed sleep sessions, newest first. */
    val sessions: StateFlow<List<SleepSession>> = repository.getAllSessions()
        .map { list -> list.filter { !it.isTracking } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Number of audio recordings captured per session, keyed by session id. */
    val recordingCounts: StateFlow<Map<Long, Int>> = repository.getAllRecordings()
        .map { recs -> recs.mapNotNull { it.sessionId }.groupingBy { it }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}
