# Sleep Analyzer Feature Inventory

This file catalogs the user-facing features implemented in the generated Android app.

## App shell and navigation

- Compose Material3 app shell with a sleep-focused dark theme.
- Bottom navigation tabs for Home, Track, Sounds, Alarm, and More.
- Navigation routes for detail screens: sleep result, sound player, recorder, stats, notes, goals, programs, game, and alarm ringing.
- **Representative files:** `MainActivity.kt`, `ui\navigation\Screen.kt`, `ui\theme\Color.kt`, `ui\theme\Theme.kt`

## Home dashboard

- Greeting based on time of day.
- Last-night sleep summary card.
- Weekly average sleep score and duration cards.
- Sleep quality trend chart.
- Quick actions for tracking, sounds, and recorder.
- Total tracked nights counter.
- **Representative files:** `ui\home\HomeScreen.kt`, `ui\home\HomeViewModel.kt`

## Sleep tracking

- Start/stop sleep tracking workflow.
- Foreground service for long-running sleep tracking.
- Accelerometer-based motion monitoring.
- Sleep quality score from 1 to 100.
- Estimated deep, light, REM, and awake minutes.
- Interruption counting.
- Mood-before and mood-after data model and UI.
- Recent nights carousel.
- Detailed sleep report screen with score ring, duration, stages, interruptions, and mood.
- **Representative files:** `service\SleepTrackingService.kt`, `ui\tracker\SleepTrackerScreen.kt`, `ui\tracker\SleepResultScreen.kt`, `ui\tracker\SleepTrackerViewModel.kt`

## Smart alarm

- Alarm list with add, edit, delete, and enable/disable controls.
- 24-hour time picker for alarm setup.
- Configurable wake-up window from 0 to 90 minutes.
- Repeat-day selection.
- Alarm label, vibration, and snooze settings.
- Exact alarm scheduling through `AlarmManager`.
- Boot receiver intended to reschedule enabled alarms after reboot.
- Full-screen alarm ringing UI with snooze and dismiss buttons.
- **Representative files:** `alarm\AlarmScheduler.kt`, `alarm\AlarmReceiver.kt`, `alarm\BootReceiver.kt`, `ui\alarm\AlarmScreen.kt`, `ui\alarm\AlarmRingingScreen.kt`, `ui\alarm\AlarmViewModel.kt`

## Sleep recorder and snore tracker

- Foreground audio recording service.
- 30-second clip rotation for sleep audio.
- Amplitude-based classification for snore, cough, noise, and unknown clips.
- Local recording persistence in Room.
- Recorder screen with animated waveform indicator.
- Counts for snore, cough, and noise events.
- Recording playback and delete actions.
- **Representative files:** `service\AudioRecorderService.kt`, `ui\recorder\RecorderScreen.kt`, `ui\recorder\RecorderViewModel.kt`

## Automatic bedtime detection

- Opt-in "Detect bedtime automatically" mode that watches screen-off + accelerometer-still + microphone-quiet and starts a tracking session on its own after a sustained quiet window (default 15 minutes).
- Conservative, deterministic state machine — resets on any activity (screen turns on, movement above threshold, ambient noise) and triggers at most once per quiet window.
- Restarts after device reboot when the toggle is enabled.
- **Representative files:** `sleep\BedtimeDetector.kt`, `service\BedtimeDetectionService.kt`

## Microphone-based sleep staging (signal-only)

- Opt-in "Use microphone for sleep staging" mode: when tracking starts, the recorder also runs in **signal-only** mode, feeding breathing / movement / snore signals into the sleep-stage estimator.
- Signal-only mode retains **no audio** — clips are never encoded or persisted; only derived staging signals are used.
- A single toggle starts and stops both the tracker and the recorder together.
- **Representative files:** `service\SleepTrackingService.kt`, `service\AudioRecorderService.kt`, `audio\analysis\MicSleepSignalAggregator.kt`

## Health context insights

- Opt-in "Detailed health context" mode reads caffeine, hydration, and body-temperature from Health Connect through a separate permission set that never affects the core grant.
- Surfaces plain-language notes in a "Health context" card on the sleep report — informational only, never fabricated score changes.
- **Representative files:** `wearables\HealthContextInsights.kt`, `wearables\HealthConnectSource.kt`, `ui\tracker\SleepResultScreen.kt`

## Sleep sounds and music

- Sound category catalog for white noise, pink noise, green noise, rain, nature, ASMR, meditation, bedtime stories, and sleep music.
- Per-category sound list.
- Now-playing bar in the sound library.
- Sound player UI with volume slider.
- Sleep timer choices for 15, 30, 45, 60, and 90 minutes.
- Fade-out timer logic in the background sound service.
- **Representative files:** `sounds\SoundLibrary.kt`, `service\SoundPlayerService.kt`, `ui\sounds\SoundsLibraryScreen.kt`, `ui\sounds\SoundPlayerScreen.kt`, `ui\sounds\SoundsViewModel.kt`

## Statistics and reports

- Detailed statistics screen.
- Week, month, and all-time filters.
- Average score, average duration, average deep sleep, and average interruptions.
- Quality score trend line chart.
- Duration bar chart.
- Best-night and needs-improvement highlight cards.
- **Representative files:** `ui\stats\DetailedStatsScreen.kt`, `ui\stats\StatsViewModel.kt`

## Sleep notes

- Add and delete sleep notes.
- Free-text notes.
- Tag selection for coffee, stress, exercise, alcohol, late meal, screen time, medication, nap, travel, and work.
- Notes list with date and tag chips.
- **Representative files:** `ui\notes\SleepNotesScreen.kt`, `ui\notes\NotesViewModel.kt`

## Sleep goals

- Active sleep goal model.
- Bedtime and wake-time targets.
- Target duration calculation.
- Target quality score.
- Goal view and edit dialog.
- **Representative files:** `ui\goals\SleepGoalScreen.kt`, `ui\goals\GoalsViewModel.kt`

## Sleep programs

- Seeded 7-day programs for stress relief, bedroom environment, digital detox, sleep hygiene, and deep relaxation.
- Program start action.
- Day-by-day progress tracking.
- Completion state.
- **Representative files:** `ui\programs\SleepProgramsScreen.kt`, `data\db\entity\SleepProgram.kt`

## Alertness game

- Morning reaction-time game.
- Five-round test flow.
- Too-early detection.
- Average and best reaction time results.
- Alertness label based on reaction speed.
- Replay action.
- **Representative file:** `ui\games\AlertnessGameScreen.kt`

## Data layer

- Room database with entities for sleep sessions, notes, alarms, audio recordings, goals, and programs.
- DAO coverage for create, update, delete, and query operations.
- Repository wrapper used by ViewModels and services.
- **Representative files:** `data\db\AppDatabase.kt`, `data\db\entity\*.kt`, `data\db\dao\*.kt`, `data\repository\SleepRepository.kt`

## Privacy notice and settings

- In-app privacy notice (Settings → Privacy) explaining in plain language what stays on device, what syncs to Firebase only when cloud sync is opted in, the "raw audio never leaves the device" guarantee, the Health Connect read scope, and how to request export / deletion.
- Settings toggles for automatic bedtime detection, microphone-based staging, and detailed health context.
- **Representative files:** `ui\settings\PrivacyScreen.kt`, `ui\settings\SettingsScreen.kt`
