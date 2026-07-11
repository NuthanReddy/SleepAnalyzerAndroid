package tech.future.sleepanalyzer.ui.tracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.db.entity.AudioRecording
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.wearables.HealthContextInsight
import tech.future.sleepanalyzer.wearables.HealthContextInsights
import tech.future.sleepanalyzer.wearables.WearableMetric

class SleepResultViewModel(application: Application, sessionId: Long) : AndroidViewModel(application) {
    private val repository = SleepRepository(application)

    val session: StateFlow<SleepSession?> = repository.getSessionByIdFlow(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Audio events (snore/cough/talk/noise) captured during this session, oldest first. */
    val recordings: StateFlow<List<AudioRecording>> = repository.getRecordingsBySession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Opt-in "Detailed health context" notes (#7); empty unless the user logged such data. */
    val healthInsights: StateFlow<List<HealthContextInsight>> = session
        .map { s -> if (s == null) emptyList() else loadInsights(s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private suspend fun loadInsights(s: SleepSession): List<HealthContextInsight> {
        val start = s.startTime
        val end = (s.endTime ?: (start + s.durationMinutes * 60_000L)).coerceAtLeast(start + 1)
        val caffeineWindowStart = start - HealthContextInsights.LATE_CAFFEINE_HOURS * 3_600_000L
        val caffeine = repository.getWearableSamplesInRange(caffeineWindowStart, end, WearableMetric.CAFFEINE.name)
            .map { it.timestamp to it.value }
        val hydration = repository.getWearableSamplesInRange(start, end, WearableMetric.HYDRATION.name)
            .map { it.timestamp to it.value }
        val bodyTemp = repository.getWearableSamplesInRange(start, end, WearableMetric.BODY_TEMPERATURE.name)
            .map { it.timestamp to it.value }
        return HealthContextInsights.build(start, caffeine, hydration, bodyTemp)
    }
}

class SleepResultViewModelFactory(private val sessionId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        throw UnsupportedOperationException("Use the create(modelClass, extras) overload")
    }

    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: androidx.lifecycle.viewmodel.CreationExtras
    ): T {
        val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
        @Suppress("UNCHECKED_CAST")
        return SleepResultViewModel(application, sessionId) as T
    }
}
