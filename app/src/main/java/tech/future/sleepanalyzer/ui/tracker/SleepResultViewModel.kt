package tech.future.sleepanalyzer.ui.tracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.repository.SleepRepository

class SleepResultViewModel(application: Application, sessionId: Long) : AndroidViewModel(application) {
    private val repository = SleepRepository(application)

    val session: StateFlow<SleepSession?> = repository.getSessionByIdFlow(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
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
