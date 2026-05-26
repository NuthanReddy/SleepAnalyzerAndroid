package tech.future.sleepanalyzer.ui.alarm

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.alarm.AlarmScheduler
import tech.future.sleepanalyzer.data.db.entity.AlarmConfig
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.service.AlarmPlaybackService
import tech.future.sleepanalyzer.util.Constants

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SleepRepository(application)
    private val scheduler = AlarmScheduler(application)

    val alarms: StateFlow<List<AlarmConfig>> = repository.getAllAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleResult = MutableSharedFlow<Boolean>()

    private val _selectedAlarm = MutableStateFlow<AlarmConfig?>(null)
    val selectedAlarm: StateFlow<AlarmConfig?> = _selectedAlarm

    private val _editHour = MutableStateFlow(7)
    val editHour: StateFlow<Int> = _editHour

    private val _editMinute = MutableStateFlow(0)
    val editMinute: StateFlow<Int> = _editMinute

    private val _editWakeWindow = MutableStateFlow(30)
    val editWakeWindow: StateFlow<Int> = _editWakeWindow

    private val _editUseSmartWake = MutableStateFlow(true)
    val editUseSmartWake = _editUseSmartWake.asStateFlow()

    private val _editDays = MutableStateFlow(setOf(1, 2, 3, 4, 5))
    val editDays: StateFlow<Set<Int>> = _editDays

    private val _editLabel = MutableStateFlow("")
    val editLabel: StateFlow<String> = _editLabel

    private val _editSound = MutableStateFlow("gentle_chimes")
    val editSound: StateFlow<String> = _editSound

    private val _editVibration = MutableStateFlow(true)
    val editVibration: StateFlow<Boolean> = _editVibration

    private val _editSnooze = MutableStateFlow(true)
    val editSnooze: StateFlow<Boolean> = _editSnooze

    private val _showEditor = MutableStateFlow(false)
    val showEditor: StateFlow<Boolean> = _showEditor

    fun onNewAlarm() {
        _selectedAlarm.value = null
        _editHour.value = 7
        _editMinute.value = 0
        _editWakeWindow.value = 30
        _editUseSmartWake.value = true
        _editDays.value = setOf(1, 2, 3, 4, 5)
        _editLabel.value = ""
        _editSound.value = "gentle_chimes"
        _editVibration.value = true
        _editSnooze.value = true
        _showEditor.value = true
    }

    fun onEditAlarm(alarm: AlarmConfig) {
        _selectedAlarm.value = alarm
        _editHour.value = alarm.hour
        _editMinute.value = alarm.minute
        _editWakeWindow.value = alarm.wakeWindowMinutes
        _editUseSmartWake.value = alarm.useSmartWake
        _editDays.value = alarm.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        _editLabel.value = alarm.label
        _editSound.value = alarm.soundName
        _editVibration.value = alarm.isVibrationEnabled
        _editSnooze.value = alarm.snoozeEnabled
        _showEditor.value = true
    }

    fun onDismissEditor() {
        _showEditor.value = false
    }

    fun onHourChanged(h: Int) {
        _editHour.value = h
    }

    fun onMinuteChanged(m: Int) {
        _editMinute.value = m
    }

    fun onWakeWindowChanged(w: Int) {
        _editWakeWindow.value = w
    }

    fun onUseSmartWakeChanged(v: Boolean) {
        _editUseSmartWake.value = v
    }

    fun onLabelChanged(l: String) {
        _editLabel.value = l
    }

    fun onSoundChanged(s: String) {
        _editSound.value = s
    }

    fun onVibrationChanged(v: Boolean) {
        _editVibration.value = v
    }

    fun onSnoozeChanged(s: Boolean) {
        _editSnooze.value = s
    }

    fun onDayToggled(day: Int) {
        val current = _editDays.value.toMutableSet()
        if (current.contains(day)) current.remove(day) else current.add(day)
        _editDays.value = current
    }

    fun saveAlarm() {
        viewModelScope.launch {
            val existing = _selectedAlarm.value
            val alarm = AlarmConfig(
                id = existing?.id ?: 0,
                hour = _editHour.value,
                minute = _editMinute.value,
                wakeWindowMinutes = _editWakeWindow.value,
                daysOfWeek = _editDays.value.sorted().joinToString(","),
                label = _editLabel.value,
                soundName = _editSound.value,
                isVibrationEnabled = _editVibration.value,
                snoozeEnabled = _editSnooze.value,
                isEnabled = existing?.isEnabled ?: true,
                snoozeDurationMinutes = existing?.snoozeDurationMinutes ?: Constants.DEFAULT_SNOOZE_MINUTES,
                useSmartWake = _editUseSmartWake.value
            )
            val savedAlarm = if (existing == null) {
                alarm.copy(id = repository.insertAlarm(alarm))
            } else {
                repository.updateAlarm(alarm)
                alarm
            }
            if (savedAlarm.isEnabled) {
                if (!scheduler.schedule(savedAlarm)) {
                    scheduleResult.emit(false)
                }
            } else {
                scheduler.cancel(savedAlarm.id)
            }
            _showEditor.value = false
        }
    }

    fun toggleAlarm(alarm: AlarmConfig) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = !alarm.isEnabled)
            repository.updateAlarm(updated)
            if (updated.isEnabled) {
                if (!scheduler.schedule(updated)) {
                    scheduleResult.emit(false)
                }
            } else {
                scheduler.cancel(updated.id)
            }
        }
    }

    fun deleteAlarm(alarm: AlarmConfig) {
        viewModelScope.launch {
            scheduler.cancel(alarm.id)
            repository.deleteAlarm(alarm)
        }
    }

    fun dismissAlarm(context: Context, alarmId: Long) {
        val serviceIntent = Intent(context.applicationContext, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_STOP
            putExtra(AlarmPlaybackService.EXTRA_ALARM_ID, alarmId)
        }
        context.applicationContext.startService(serviceIntent)
    }

    fun snoozeAlarm(context: Context, alarmId: Long) {
        val serviceIntent = Intent(context.applicationContext, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_SNOOZE
            putExtra(AlarmPlaybackService.EXTRA_ALARM_ID, alarmId)
        }
        context.applicationContext.startService(serviceIntent)
    }
}
