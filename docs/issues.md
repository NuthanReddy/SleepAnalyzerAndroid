# Sleep Analyzer Issues Review

This file tracks issues found during reviews of the app. Each entry has a **Status** marker so we can see what's resolved and what still needs attention. Issues with `[RESOLVED]` are kept for history.

## Critical issues

1. **[RESOLVED]** **Runtime permissions are declared but never requested**
   - **Files (then):** `AndroidManifest.xml`, recorder/tracker/alarm screens
   - **Resolution:** `util.PermissionsUtil` + Accompanist `rememberMultiplePermissionsState` gates added in onboarding (Permissions step) and on every UI that starts a service. Exact alarms checked via `AlarmManager.canScheduleExactAlarms()` with a Settings deep-link fallback.

2. **[RESOLVED]** **Alarm full-screen intent does not navigate to the ringing screen**
   - **Resolution:** `MainActivity` now reads `EXTRA_NAVIGATE_TO` / `EXTRA_ALARM_ID` from the launch intent (and `onNewIntent`) and routes to `Screen.AlarmRinging.createRoute(alarmId)`. Alarm receiver and SmartWakeService both attach the deep-link extras to the full-screen PendingIntent.

3. **[RESOLVED]** **Audio recorder can start without permission and silently fail**
   - **Resolution:** Recorder Start button is gated by `PermissionsUtil.recorderPermissions()`. Service no longer relies on lazy permission acquisition.

## High-priority issues

1. **[RESOLVED]** **MediaRecorder release is not guaranteed when `stop()` throws**
   - **Resolution:** Old `MediaRecorder`-based recorder is gone. The new `AudioRecorderService` uses `AudioRecord` PCM capture wrapped in the modular `AudioPipeline`; encoding happens via `PcmToM4aEncoder` (with WAV fallback) so resource ownership is explicit and pooled.

2. **[RESOLVED]** **Boot alarm rescheduling uses an unsafe BroadcastReceiver coroutine**
   - **Resolution:** `BootReceiver` now uses `goAsync()` with `PendingResult.finish()`, reschedules from `SleepRepository.getEnabledAlarmsList()`, and listens to both `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED` (direct-boot aware).

3. **[RESOLVED]** **Alarm audio can continue indefinitely**
   - **Resolution:** New `service/AlarmPlaybackService` (foreground mediaPlayback type) owns MediaPlayer + Vibrator, with explicit Dismiss/Snooze notification actions.

4. **[RESOLVED]** **Sleep tracking wake lock is capped at 8 hours**
   - **Resolution:** `SleepTrackingService` now calls `wakeLock.acquire()` without a timeout and releases on explicit stop / `onDestroy`.

5. **[RESOLVED]** **Mood-after flow saves before the user can answer**
   - **Resolution:** Stop flow now opens the mood selector first; `stopTracking()` runs only after the user picks a mood (or Skip), then navigation to SleepResult fires via a one-shot Channel-backed Flow.

## Functional gaps and UX issues

1. **[RESOLVED]** **Sleep sounds library does not play real sound**
   - **Resolution:** New `audio/sound/` package with `WhiteNoiseGenerator`, `PinkNoiseGenerator`, `BrownNoiseGenerator`, `RainGenerator`, `OceanWaveGenerator` and `SoundGeneratorFactory`. `SoundPlayerService` streams generated PCM through `AudioTrack` on `AudioDispatchers.processing`, with live volume and fade-out timer.

2. **[RESOLVED]** **Smart alarm is not actually sleep-cycle aware**
   - **Resolution:** New `sleep/` package + `service/SmartWakeService` + `alarm/SmartAlarmReceiver`. `AlarmScheduler` schedules WINDOW_START and DEADLINE alarms when `AlarmConfig.useSmartWake` is true. SmartWakeService runs a 30 s loop inside the window, builds `SleepSignals` from `MotionMonitor` + recent HR/HRV samples, asks `SleepStageEstimator` for the current stage, and `SmartWakeAnalyzer` decides FIRE_NOW / WAIT / FIRE_AT_DEADLINE. Per-alarm toggle exposed in the editor.

3. **[RESOLVED]** **Snooze uses editor state instead of fired alarm configuration**
   - **Resolution:** `AlarmRingingScreen` now receives `alarmId`. Snooze routes through `AlarmPlaybackService.ACTION_SNOOZE`, which looks up the real `AlarmConfig` and re-schedules at `snoozeDurationMinutes`.

4. **[RESOLVED]** **Sleep recorder is not tied to sleep sessions**
   - **Resolution:** `AudioRecorderService` queries `repository.getActiveSession()` on start and stamps `sessionId` on each persisted recording.

5. **[RESOLVED]** **Snore/cough/talk detection is amplitude-only**
   - **Resolution:** New Strategy-based `audio/classification/`. `SpectralClassifier` uses RMS, peak, zero-crossing rate, spectral centroid, spectral flatness, autocorrelation pitch, and periodicity to separate SNORE / COUGH / TALK / NOISE / SILENCE. `AmplitudeClassifier` kept as a low-power fallback. Selection is via `ClassifierFactory` so a TFLite model can drop in later.

6. **[RESOLVED]** **"Who's snoring?" is not implemented**
   - **Resolution:** Optional voice enrollment in onboarding captures the user's voice and persists a `VoiceProfile` (pitch mean/std + spectral centroid + mel band energies). `ProfileVoiceMatcher` uses cosine similarity on mel bands + pitch distance to attribute each saved recording to USER, PARTNER, or UNKNOWN. `VoiceMatcherFactory` returns `NoOpVoiceMatcher` when the user opts out, so isolation is fully optional.

7. **[RESOLVED]** **Home "Last Night" lookup uses today's date**
   - **Resolution:** `HomeViewModel` now uses `SleepRepository.getMostRecentCompletedSession()`.

8. **Sleep-stage detection is a rough heuristic**
   - **Status:** Partially mitigated. We now have two estimators: `MotionOnlySleepStageEstimator` and `MultiSignalSleepStageEstimator` (motion + HR + HRV + respiration). `SleepCyclePredictor` is age-adjusted from the new `UserProfile`. Still a heuristic, not clinical — a TFLite stage classifier can replace either implementation behind the same `SleepStageEstimator` interface when one becomes available.

9. **[RESOLVED]** **Accelerometer gravity removal is simplistic**
   - **Resolution:** `SleepTrackingService` and `MotionMonitor` prefer `TYPE_LINEAR_ACCELERATION` (already gravity-compensated) and fall back to a low-pass-filter gravity removal on `TYPE_ACCELEROMETER` when LINEAR is unavailable.

10. **[RESOLVED]** **Exact alarm scheduling lacks capability checks**
    - **Resolution:** `AlarmScheduler.schedule()` checks `PermissionsUtil.canScheduleExactAlarms(context)` and falls back to `setAndAllowWhileIdle` on `SecurityException`, returning a Boolean so the UI can surface the system Settings intent.

11. **[RESOLVED]** **Program content is stored as raw JSON strings**
    - **Resolution:** `SleepProgramsScreen` parses the JSON step array into a list and highlights the current day's step in the expanded card.

12. **Online backup is not implemented**
    - **Status:** Open. Local Room storage is the source of truth. Adding cloud sync is intentionally deferred — would require accounts, a backend, and additional permissions. Will be addressed later if user demand surfaces.

13. **[RESOLVED]** **Google Fit integration is not implemented**
    - **Resolution:** Replaced "Google Fit" with the modern Health Connect path. New `wearables/` package: `WearableSource` Strategy, `HealthConnectSource` reading HeartRate / HRV / Respiratory Rate / SpO2 / Steps, `SourceRegistry` (lazy provider list, easy to add Samsung/Fitbit/Garmin/Wear OS later), `WearableSyncManager` + `WearableSyncWorker` (WorkManager periodic sync) + `WearableScheduler` (periodic enqueue from MainActivity). One-shot incremental sync also fires while `SleepTrackingService` is active.

14. **Wear OS support is not implemented**
    - **Status:** Open. The Wear OS module would still be valuable but is out of scope for the current pass. Note: Pixel Watch / many Samsung / Garmin watches already write to Health Connect, so most users now get watch-derived signals automatically via the wearables path above.

15. **No automated tests cover the generated behavior**
    - **Status:** Open. Repositories, scoring, alarm scheduling, classifier strategies, and SmartWakeAnalyzer are all pure or near-pure and would be easy to test. Top priority: unit tests for `SpectralClassifier`, `ProfileVoiceMatcher`, `AlarmScheduler.calculateNextTriggerTime`, `SleepCyclePredictor`, and `SmartWakeAnalyzer.decide`.

## New items surfaced during the rebuild

These are not regressions but follow-ups worth tracking:

- **A. APK size verification under R8.** Release builds are configured with `isMinifyEnabled = true` and `isShrinkResources = true`, but we haven't profiled the actual size. Need a baseline release APK measurement + ProGuard rule review for `androidx.health.connect`, `androidx.work`, and Compose.
- **B. Health Connect skin temperature.** `SkinTemperatureRecord` is gated behind a newer Health Connect SDK than the one we pin. The enum + sync infrastructure is ready; flipping it on is a one-line addition once we bump `healthConnect` past `1.1.0-alpha07`.
- **C. [RESOLVED] Profile-driven scoring weights.** Extracted scoring logic into pure `sleep/SleepQualityScorer` object so it can be unit-tested. `SleepTrackingService.stopTracking()` now passes the active `UserProfile` so deep-sleep targets and the ideal duration window adapt by `activityLevel` (athlete/active vs. moderate vs. light/sedentary). Athletes are expected to consolidate ~30% deep sleep; sedentary users ~18%. The Kotlin scorer is fully covered by `SleepQualityScorerTest`.
- **D. [RESOLVED] Snooze for SmartWake-triggered alarms.** Introduced `Constants.EXTRA_ORIGINAL_TARGET_MS` so the user's actual target wake time threads from the alarm trigger (DEADLINE alarm = `nowMs`; SmartWakeService = stored `currentWindowEndMs`) into `AlarmPlaybackService`. `snoozeAlarm()` now anchors to `max(originalTargetMs, nowMs)` + snooze minutes, with a `nowMs + 60s` safety floor. The notification Snooze action also carries the extra so it behaves identically to the in-app button. Tracked in backlog #10.
- **E. [RESOLVED] Recording playback MediaPlayer leak.** `RecorderViewModel.stopPlayback()` previously called `mediaPlayer.stop()` before `release()` without a `try/finally`; if `stop()` threw `IllegalStateException`, the player was leaked. Fixed by always releasing in `finally`.
