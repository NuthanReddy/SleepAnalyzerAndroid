package tech.future.sleepanalyzer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tech.future.sleepanalyzer.MainActivity
import tech.future.sleepanalyzer.R
import tech.future.sleepanalyzer.audio.AudioDispatchers
import tech.future.sleepanalyzer.audio.sound.SoundGenerator
import tech.future.sleepanalyzer.audio.sound.SoundGeneratorFactory
import tech.future.sleepanalyzer.util.Constants

class SoundPlayerService : Service() {

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val sampleRate = 22050
    private var generator: SoundGenerator? = null
    private var volume = 0.7f
    private val serviceScope = CoroutineScope(SupervisorJob())
    private var fadeJob: Job? = null

    companion object {
        const val ACTION_PLAY = "play_sound"
        const val ACTION_STOP = "stop_sound"
        const val ACTION_SET_TIMER = "set_timer"
        const val ACTION_SET_VOLUME = "set_volume"
        const val EXTRA_SOUND_NAME = "sound_name"
        const val EXTRA_TIMER_MINUTES = "timer_minutes"
        const val EXTRA_VOLUME = "volume"

        var isPlaying = false
            private set
        var currentSoundName: String? = null
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val soundName = intent.getStringExtra(EXTRA_SOUND_NAME) ?: return START_NOT_STICKY
                playSoundLoop(soundName)
            }
            ACTION_STOP -> stopSound()
            ACTION_SET_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_TIMER_MINUTES, 30)
                startTimer(minutes)
            }
            ACTION_SET_VOLUME -> {
                volume = intent.getFloatExtra(EXTRA_VOLUME, volume).coerceIn(0f, 1f)
                audioTrack?.setTrackVolume(volume)
            }
        }
        return START_STICKY
    }

    private fun playSoundLoop(soundName: String) {
        releasePlayback(stopService = false)
        createNotificationChannel()

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            stopSound()
            return
        }

        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBufferSize * 2,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            stopSound()
            return
        }

        val selectedGenerator = SoundGeneratorFactory.create(soundName, sampleRate)
        generator = selectedGenerator
        audioTrack = track

        track.setTrackVolume(volume)
        track.play()

        currentSoundName = soundName
        isPlaying = true

        val notification = createNotification("Playing: ${soundName.replace("_", " ")}")
        ServiceCompat.startForeground(
            this,
            Constants.SOUND_PLAYER_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        playbackJob = serviceScope.launch(AudioDispatchers.processing) {
            val buffer = ShortArray(1024)
            try {
                while (isActive) {
                    selectedGenerator.next(buffer, buffer.size)
                    val written = track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) break
                }
            } catch (_: IllegalStateException) {
            } finally {
                if (isActive) {
                    serviceScope.launch { stopSound() }
                }
            }
        }
    }

    private fun startTimer(minutes: Int) {
        fadeJob?.cancel()
        fadeJob = serviceScope.launch {
            val fadeStartDelay = ((minutes * 60 - 30) * 1000L).coerceAtLeast(0L)
            delay(fadeStartDelay)

            val startingVolume = volume.coerceIn(0f, 1f)
            var fadeVolume = startingVolume
            val steps = 30
            val stepDelay = 1000L
            repeat(steps) {
                fadeVolume -= (startingVolume / steps)
                audioTrack?.setTrackVolume(fadeVolume)
                delay(stepDelay)
            }
            stopSound()
        }
    }

    private fun stopSound() {
        releasePlayback(stopService = true)
    }

    private fun releasePlayback(stopService: Boolean) {
        fadeJob?.cancel()
        fadeJob = null

        playbackJob?.cancel()
        playbackJob = null

        val track = audioTrack
        audioTrack = null
        generator = null
        isPlaying = false
        currentSoundName = null

        try {
            track?.stop()
        } catch (_: IllegalStateException) {
        }
        try {
            track?.flush()
        } catch (_: IllegalStateException) {
        }
        try {
            track?.release()
        } catch (_: Exception) {
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        if (stopService) stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.SOUND_PLAYER_CHANNEL_ID,
            "Sound Player",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows while playing sleep sounds" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.SOUND_PLAYER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sleep Sounds")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun AudioTrack.setTrackVolume(level: Float) {
        setVolume(level.coerceIn(0f, 1f))
    }

    override fun onDestroy() {
        releasePlayback(stopService = false)
        serviceScope.cancel()
        super.onDestroy()
    }
}
