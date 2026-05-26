package tech.future.sleepanalyzer.ui.sounds

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tech.future.sleepanalyzer.service.SoundPlayerService
import tech.future.sleepanalyzer.sounds.SoundCategory
import tech.future.sleepanalyzer.sounds.SoundItem
import tech.future.sleepanalyzer.sounds.SoundLibrary

class SoundsViewModel(application: Application) : AndroidViewModel(application) {
    val categories: List<SoundCategory> = SoundLibrary.categories

    private val _isPlaying = MutableStateFlow(SoundPlayerService.isPlaying)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentSound = MutableStateFlow<String?>(SoundPlayerService.currentSoundName)
    val currentSound: StateFlow<String?> = _currentSound

    private val _timerMinutes = MutableStateFlow(30)
    val timerMinutes: StateFlow<Int> = _timerMinutes

    private val _volume = MutableStateFlow(0.7f)
    val volume: StateFlow<Float> = _volume

    fun playSound(sound: SoundItem) {
        val app = getApplication<Application>()
        val intent = Intent(app, SoundPlayerService::class.java).apply {
            action = SoundPlayerService.ACTION_PLAY
            putExtra(SoundPlayerService.EXTRA_SOUND_NAME, sound.id)
        }
        app.startForegroundService(intent)
        _isPlaying.value = true
        _currentSound.value = sound.id
    }

    fun stopSound() {
        val app = getApplication<Application>()
        val intent = Intent(app, SoundPlayerService::class.java).apply {
            action = SoundPlayerService.ACTION_STOP
        }
        app.startService(intent)
        _isPlaying.value = false
        _currentSound.value = null
    }

    fun setTimer(minutes: Int) {
        _timerMinutes.value = minutes
        if (_isPlaying.value) {
            val app = getApplication<Application>()
            val intent = Intent(app, SoundPlayerService::class.java).apply {
                action = SoundPlayerService.ACTION_SET_TIMER
                putExtra(SoundPlayerService.EXTRA_TIMER_MINUTES, minutes)
            }
            app.startService(intent)
        }
    }

    fun setVolume(vol: Float) {
        _volume.value = vol
        if (_isPlaying.value) {
            val app = getApplication<Application>()
            val intent = Intent(app, SoundPlayerService::class.java).apply {
                action = SoundPlayerService.ACTION_SET_VOLUME
                putExtra(SoundPlayerService.EXTRA_VOLUME, vol)
            }
            app.startService(intent)
        }
    }

    fun getCategoryById(id: String): SoundCategory? = SoundLibrary.getCategoryById(id)
}
