package tech.future.sleepanalyzer.ui.wearables

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.data.db.entity.WearableDevice
import tech.future.sleepanalyzer.di.ServiceLocator
import tech.future.sleepanalyzer.wearables.SourceRegistry
import tech.future.sleepanalyzer.wearables.WearableSource

class WearablesViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository
        get() = ServiceLocator.repository

    private var cachedSources: List<WearableSource> = emptyList()

    private val _availableSources = MutableStateFlow<List<WearableSource>>(emptyList())
    val availableSources: StateFlow<List<WearableSource>> = _availableSources.asStateFlow()

    val connectedDevices: StateFlow<List<WearableDevice>> = repository.observeActiveWearableDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    private val _lastSyncCount = MutableStateFlow<Int?>(null)
    val lastSyncCount: StateFlow<Int?> = _lastSyncCount.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            cachedSources = SourceRegistry.all(appContext)
            _availableSources.value = SourceRegistry.available(appContext)
            _permissionsGranted.value = _availableSources.value.firstOrNull()?.hasPermissions() == true
        }
    }

    fun recheckPermissions() {
        viewModelScope.launch(Dispatchers.IO) {
            val sources = _availableSources.value.ifEmpty {
                SourceRegistry.available(appContext).also { _availableSources.value = it }
            }
            _permissionsGranted.value = sources.firstOrNull()?.hasPermissions() == true
        }
    }

    fun syncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            _lastSyncCount.value = runCatching {
                ServiceLocator.wearableSyncManager().syncIncremental()
            }.getOrNull()
        }
    }

    suspend fun requiredPermissions(): Set<String> {
        val sources = cachedSources.ifEmpty {
            SourceRegistry.all(appContext).also { cachedSources = it }
        }
        return sources.flatMap { source ->
            runCatching { source.requiredPermissions().toList() }.getOrDefault(emptyList())
        }.toSet()
    }
}
