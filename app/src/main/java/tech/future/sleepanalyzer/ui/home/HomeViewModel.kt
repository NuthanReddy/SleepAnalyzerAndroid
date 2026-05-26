package tech.future.sleepanalyzer.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.repository.SleepRepository
import java.text.SimpleDateFormat
import java.util.*

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SleepRepository(application)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val lastNight: StateFlow<SleepSession?> = repository.getMostRecentCompletedSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentSessions: StateFlow<List<SleepSession>> = repository.getRecentSessions(7)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val weekDates: Pair<String, String> get() {
        val cal = Calendar.getInstance()
        val end = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val start = dateFormat.format(cal.time)
        return start to end
    }

    val weeklyAvgScore: StateFlow<Float?> = repository.getAverageScore(weekDates.first, weekDates.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val weeklyAvgDuration: StateFlow<Float?> = repository.getAverageDuration(weekDates.first, weekDates.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalSessions: StateFlow<Int> = repository.getTotalSessionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
