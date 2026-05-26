package tech.future.sleepanalyzer.ui.goals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.data.db.entity.SleepGoal
import tech.future.sleepanalyzer.data.repository.SleepRepository

class GoalsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SleepRepository(application)

    val activeGoal: StateFlow<SleepGoal?> = repository.getActiveGoal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _editBedtimeHour = MutableStateFlow(23)
    val editBedtimeHour: StateFlow<Int> = _editBedtimeHour

    private val _editBedtimeMinute = MutableStateFlow(0)
    val editBedtimeMinute: StateFlow<Int> = _editBedtimeMinute

    private val _editWakeHour = MutableStateFlow(7)
    val editWakeHour: StateFlow<Int> = _editWakeHour

    private val _editWakeMinute = MutableStateFlow(0)
    val editWakeMinute: StateFlow<Int> = _editWakeMinute

    private val _editTargetScore = MutableStateFlow(80)
    val editTargetScore: StateFlow<Int> = _editTargetScore

    private val _showEditor = MutableStateFlow(false)
    val showEditor: StateFlow<Boolean> = _showEditor

    fun showGoalEditor() {
        val goal = activeGoal.value
        if (goal != null) {
            _editBedtimeHour.value = goal.targetBedtimeHour
            _editBedtimeMinute.value = goal.targetBedtimeMinute
            _editWakeHour.value = goal.targetWakeHour
            _editWakeMinute.value = goal.targetWakeMinute
            _editTargetScore.value = goal.targetScore
        }
        _showEditor.value = true
    }

    fun dismissEditor() {
        _showEditor.value = false
    }

    fun onBedtimeHourChanged(h: Int) {
        _editBedtimeHour.value = h
    }

    fun onBedtimeMinuteChanged(m: Int) {
        _editBedtimeMinute.value = m
    }

    fun onWakeHourChanged(h: Int) {
        _editWakeHour.value = h
    }

    fun onWakeMinuteChanged(m: Int) {
        _editWakeMinute.value = m
    }

    fun onTargetScoreChanged(s: Int) {
        _editTargetScore.value = s
    }

    fun saveGoal() {
        viewModelScope.launch {
            activeGoal.value?.let { repository.updateGoal(it.copy(isActive = false)) }

            val bedH = _editBedtimeHour.value
            val bedM = _editBedtimeMinute.value
            val wakeH = _editWakeHour.value
            val wakeM = _editWakeMinute.value
            val durationMin = calculateDuration(bedH, bedM, wakeH, wakeM)

            repository.insertGoal(
                SleepGoal(
                    targetBedtimeHour = bedH,
                    targetBedtimeMinute = bedM,
                    targetWakeHour = wakeH,
                    targetWakeMinute = wakeM,
                    targetDurationMinutes = durationMin,
                    targetScore = _editTargetScore.value,
                    isActive = true
                )
            )
            _showEditor.value = false
        }
    }

    private fun calculateDuration(bedH: Int, bedM: Int, wakeH: Int, wakeM: Int): Int {
        val bedMinutes = bedH * 60 + bedM
        val wakeMinutes = wakeH * 60 + wakeM
        return if (wakeMinutes > bedMinutes) {
            wakeMinutes - bedMinutes
        } else {
            (24 * 60 - bedMinutes) + wakeMinutes
        }
    }
}
