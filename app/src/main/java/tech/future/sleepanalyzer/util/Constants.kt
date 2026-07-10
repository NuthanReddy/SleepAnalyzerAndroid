package tech.future.sleepanalyzer.util

object Constants {
    // Notification channels
    const val SLEEP_TRACKING_CHANNEL_ID = "sleep_tracking"
    const val AUDIO_RECORDING_CHANNEL_ID = "audio_recording"
    const val ALARM_CHANNEL_ID = "alarm"
    const val ALARM_PLAYBACK_CHANNEL_ID = "alarm_playback"
    const val SOUND_PLAYER_CHANNEL_ID = "sound_player"
    const val BEDTIME_DETECTION_CHANNEL_ID = "bedtime_detection"

    // Notification IDs
    const val SLEEP_TRACKING_NOTIFICATION_ID = 1001
    const val AUDIO_RECORDING_NOTIFICATION_ID = 1002
    const val ALARM_NOTIFICATION_ID = 1003
    const val SOUND_PLAYER_NOTIFICATION_ID = 1004
    const val ALARM_PLAYBACK_NOTIFICATION_ID = 1005
    const val SMART_WAKE_NOTIFICATION_ID = 1006
    const val BEDTIME_DETECTION_NOTIFICATION_ID = 1007

    // Sleep scoring weights
    const val DURATION_WEIGHT = 0.35f
    const val INTERRUPTION_WEIGHT = 0.25f
    const val CONSISTENCY_WEIGHT = 0.20f
    const val DEEP_SLEEP_WEIGHT = 0.20f

    // Ideal sleep values
    const val IDEAL_SLEEP_HOURS = 8.0f
    const val MIN_SLEEP_HOURS = 6.0f
    const val MAX_SLEEP_HOURS = 10.0f

    // Audio recording configuration
    const val AUDIO_SAMPLE_RATE = 22050
    const val AUDIO_BUFFER_SECONDS = 30

    // PCM amplitude thresholds (16-bit signed: -32768..32767)
    const val PCM_SILENCE_RMS = 200
    const val PCM_NOISE_RMS = 600
    const val PCM_SNORE_RMS = 1200
    const val PCM_COUGH_RMS = 6000

    // Voice activity detection
    const val VAD_FRAME_MS = 30
    const val VAD_MIN_VOICE_MS = 250
    const val VAD_PADDING_MS = 400

    // Voice profile similarity threshold (0..1, higher = stricter match to user)
    const val VOICE_MATCH_THRESHOLD = 0.65f

    // Snore classification (Hz)
    const val SNORE_PITCH_MIN_HZ = 40f
    const val SNORE_PITCH_MAX_HZ = 250f
    const val VOICE_PITCH_MIN_HZ = 75f
    const val VOICE_PITCH_MAX_HZ = 500f

    // Alarm defaults
    const val DEFAULT_WAKE_WINDOW_MINUTES = 30
    const val MAX_WAKE_WINDOW_MINUTES = 90
    const val DEFAULT_SNOOZE_MINUTES = 9

    // Shared Preferences keys
    const val PREFS_NAME = "sleep_analyzer_prefs"

    // Intent extras
    const val EXTRA_NAVIGATE_TO = "navigate_to"
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_ORIGINAL_TARGET_MS = "original_target_ms"
    const val NAV_ALARM_RINGING = "alarm_ringing"

    // Legacy amplitude thresholds (for backward compatibility, MediaRecorder maxAmplitude)
    const val SNORE_AMPLITUDE_THRESHOLD = 8000
    const val COUGH_AMPLITUDE_THRESHOLD = 12000
    const val NOISE_AMPLITUDE_THRESHOLD = 5000
}
