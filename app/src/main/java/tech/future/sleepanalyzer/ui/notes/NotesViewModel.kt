package tech.future.sleepanalyzer.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.data.db.entity.AudioRecording
import tech.future.sleepanalyzer.data.db.entity.SleepNote
import tech.future.sleepanalyzer.data.repository.SleepRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SleepRepository(application)

    val allNotes: StateFlow<List<SleepNote>> = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Spoken-word ("talk") recordings that have a non-empty transcript — the STT log in one place. */
    val transcripts: StateFlow<List<AudioRecording>> = repository.getRecordingsByType("talk")
        .map { list -> list.filter { !it.transcript.isNullOrBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog

    private val _selectedTags = MutableStateFlow(setOf<String>())
    val selectedTags: StateFlow<Set<String>> = _selectedTags

    private val _noteText = MutableStateFlow("")
    val noteText: StateFlow<String> = _noteText

    val availableTags = listOf(
        "coffee" to "☕",
        "stress" to "😰",
        "exercise" to "🏃",
        "alcohol" to "🍷",
        "late_meal" to "🍕",
        "screen_time" to "📱",
        "medication" to "💊",
        "nap" to "😴",
        "travel" to "✈️",
        "work" to "💼"
    )

    fun showAddNote() {
        _showAddDialog.value = true
    }

    fun dismissAddNote() {
        _showAddDialog.value = false
        _selectedTags.value = emptySet()
        _noteText.value = ""
    }

    fun toggleTag(tag: String) {
        val current = _selectedTags.value.toMutableSet()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        _selectedTags.value = current
    }

    fun onNoteTextChanged(text: String) {
        _noteText.value = text
    }

    fun saveNote() {
        viewModelScope.launch {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val note = SleepNote(
                date = dateFormat.format(Date()),
                tags = _selectedTags.value.joinToString(","),
                note = _noteText.value
            )
            repository.insertNote(note)
            dismissAddNote()
        }
    }

    fun deleteNote(note: SleepNote) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }
}
