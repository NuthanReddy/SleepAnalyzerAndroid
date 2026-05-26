# Sleep Analyzer Backlog

Single source of truth for work that's **intentionally deferred** — either because it requires offline effort (datasets, GPU training, clinical studies), because it depends on other planned items, or because it has been triaged as lower priority than the in-flight workstreams.

Items here are **not bugs in shipped code** — those go in `docs/issues.md`. This file tracks "we know about it, we've thought about it, here's the plan when we pick it up."

Each entry has a fixed shape:

- **What** – the deliverable in plain English
- **Why** – the user/product value
- **Acceptance** – how we'll know it's done
- **Dependencies** – what must land first
- **Notes** – design hints, risk, cost
- **Status** – `Open` / `In progress` / `Blocked` / `Done` (when done, move it out of this file and into the architecture changelog)

---

## ML model for sleep stage classification

### 1. Training data acquisition

- **What**: Assemble a labeled corpus large enough to train the `MlSleepStageEstimator` to genuinely beat the heuristic estimator. Targets, in priority order:
  1. Opt-in passive collection from the app's own users — tuples of `(SleepSignals features, vendor stage label)` captured whenever a session has Health Connect vendor stage segments. Uploaded only when the user has signed in AND enabled cloud sync AND opted into "Help improve sleep staging".
  2. Public PSG datasets: Sleep Heart Health Study (SHHS, ~6,000 nights, requires DUA), Sleep-EDF (PhysioNet, ~200 nights, open), MESA Sleep, PSGCAP. Used to bootstrap the model before user data accumulates.
  3. (Stretch) A small in-lab data collection partnership with a sleep clinic — phone running our app side-by-side with PSG, ~50 nights, gold-standard labels. Useful for the validation item below as well as training.
- **Why**: Without labeled data the trained model can do no better than the heuristic it was bootstrapped from. The whole point of the ML path is to learn from data the heuristic can't capture (e.g. the breathing-envelope and snore-pattern joint distributions).
- **Acceptance**:
  - At least 1,000 unique user-nights of `(features, vendor_label)` tuples in the Firestore corpus.
  - At least one bootstrap dataset (SHHS or Sleep-EDF) downloaded and pre-processed into the same feature format used by the on-device model.
  - A `data_sources.md` document under `ml/training/` lists the corpus with licensing notes for each source.
- **Dependencies**:
  - `auth-and-sync` agent must ship the `training_examples` Firestore upload path with consent gating.
  - Server-side processor (out of repo scope) to receive uploads, run integrity checks, store under `gs://<bucket>/training_corpus/{date}/`.
- **Notes**:
  - SHHS access requires a Data Use Agreement with the National Sleep Research Resource (sleepdata.org). Lead time ~4 weeks.
  - Sleep-EDF is open but small; useful for sanity-check overfitting, not for production model.
  - Per-user upload caps (e.g. 1 MB/night) so a user with a runaway recorder can't dominate the corpus.
  - Privacy: features are derived (no raw audio), but still aggregate per-night summaries — disclose clearly in the consent screen and the privacy policy.
- **Status**: Open.

### 2. Real PSG validation study

- **What**: Run an independent, peer-reviewable comparison of the app's stage predictions vs polysomnography ground truth on a population that wasn't part of the training corpus.
- **Why**:
  1. To publish credible accuracy claims in marketing ("88% agreement with PSG on a held-out cohort of N=…") instead of vendor-style "trained on 7,000 nights" hand-waving.
  2. To catch failure modes the heuristic missed — e.g. apnea-driven HR variability misread as REM, REM-without-atonia patients, partner-in-bed contamination.
  3. As a precondition for any FDA / CE / TGA medical-device claims down the line (currently the app is a "wellness" device, not medical).
- **Acceptance**:
  - At least 30 participants, two nights each in their own bed, with portable home PSG running in parallel (e.g. Apnealink, Nox A1).
  - 4-stage epoch-by-epoch comparison (Cohen's kappa, per-stage sensitivity/specificity).
  - Separate sub-cohort with a snoring partner so we can assess voice-attribution accuracy.
  - A draft methods + results document in `docs/research/psg-validation.md`.
  - Optional but ideal: write-up submitted to *Journal of Sleep Research* or *Sleep Medicine*.
- **Dependencies**:
  - Ethics approval (IRB) from a participating institution.
  - PSG hardware (rented or partnered).
  - The ML model must be at v1.0+ — pointless to validate a model that's still being designed.
- **Notes**:
  - Independent third-party validation is more credible than self-published; consider commissioning a sleep lab study rather than running it ourselves.
  - Budget: ~$15K for PSG rental + tech time + IRB fees (US estimate).
  - Lead time: 6–9 months including ethics + recruitment + scoring.
  - The Fino 2020 paper (Journal of Sleep Research) is the kind of study we should be measured against and ultimately beat.
- **Status**: Open.

### 3. Model architecture iterations (after data exists)

- **What**: Beyond the seed MLP, explore better architectures once the corpus is large enough: 1D CNN on log-mel + features, CRNN, small Transformer encoder, or a borrowed open-source PSG model (e.g. U-Sleep, DeepSleepNet) adapted to our feature set.
- **Why**: The seed model will plateau quickly; serious gains need more expressive architectures and ablations.
- **Acceptance**: Best-performing architecture is selected via cross-validation on the held-out PSG cohort and exported to TFLite ≤ 1 MB.
- **Dependencies**: Items 1 and 2 above.
- **Notes**: Quantization-aware training mandatory — the model has to fit in RAM-constrained services.
- **Status**: Open.

---

## Wearables expansion

### 4. Direct Samsung Health source

- **What**: A `SamsungHealthSource` implementing `WearableSource`, using the Samsung Health Data SDK.
- **Why**: Many Galaxy users in India don't write to Health Connect — they expect Samsung Health.
- **Acceptance**: A signed-in Galaxy Watch user sees their stages and HR in the app without going through Health Connect.
- **Status**: Open.

### 5. Direct Fitbit Web API source

- **What**: `FitbitWebSource` using OAuth2 + the Fitbit Web API.
- **Why**: Fitbit's Health Connect bridge is patchy on older Android versions. Direct API is more reliable for premium users.
- **Acceptance**: User can connect their Fitbit account in Settings and pull HR/HRV/sleep stages.
- **Notes**: Requires Fitbit developer account + OAuth client; rate-limited to 150 calls/hour/user.
- **Status**: Open.

### 6. Wear OS module

- **What**: A separate Wear OS module with tile, complication, and basic tracking UI.
- **Why**: Some users want to start/stop tracking from the watch and see last-night summary on the wrist.
- **Acceptance**: APK installs from Play Store as paired Wear OS app; tile shows tonight's sleep score after wake-up; complication on watch face shows sleep status.
- **Notes**: Could be deferred indefinitely now that Pixel Watch / Galaxy already write stages back through Health Connect — our app reads them automatically.
- **Status**: Open (low priority).

### 7. Health Connect — additional metrics

- **What**: Consume `BodyTemperatureRecord` curves, `BloodPressureRecord` spot readings, `BloodGlucoseRecord` (CGM), `MenstruationFlowRecord` for cycle-aware staging, `NutritionRecord` (Caffeine subtype), `HydrationRecord`.
- **Why**: Each unlocks a niche but real insight. Caffeine + bedtime hour explains delayed sleep onset; menstrual phase explains REM/deep ratio shifts; CGM nocturnal lows explain fragmented sleep.
- **Acceptance**: Each metric appears as a chip in Sleep Result when present, and contributes a documented adjustment to scoring.
- **Notes**: Each new permission adds a prompt — bundle them into one "Detailed health context" Settings toggle so the user opts in once.
- **Status**: Open.

### 8. Skin temperature consumption

- **What**: Read `SkinTemperatureRecord` once the Health Connect SDK pinned in `libs.versions.toml` exposes it stably (currently gated behind an alpha).
- **Why**: Skin temp deviation is a great REM-vs-deep discriminator.
- **Acceptance**: HC SDK bumped, source extended, MultiSignal estimator uses temperature as one of its scoring inputs.
- **Status**: Open.

---

## Smart wake refinements

### 9. Profile-driven scoring weights

- **What**: Make sleep quality scoring adjust deep/REM expectations by `UserProfile.activityLevel`. Athletes are *expected* to have more deep sleep; sedentary users have less.
- **Why**: Today the score is one-size-fits-all and unfairly penalizes anyone outside the population average.
- **Acceptance**: A logged athlete and a sedentary user with identical raw signals get scores that reflect their respective normals.
- **Status**: **Fixed**. Extracted into `sleep/SleepQualityScorer` (pure object, unit-testable). `SleepTrackingService.stopTracking()` now reads `repository.getUserProfile()` and passes it in; `targetDeepRatio` and `idealDurationRange` flex by activity level (athlete=30% deep target / 7.5–9.5 h ideal, moderate=22%/7–9 h, sedentary=18%/6.5–8.5 h). Covered by `SleepQualityScorerTest`.

### 10. Smart-wake snooze logic

- **What**: When SmartWake fires early (say, 25 minutes before the deadline target) and the user snoozes, the next snooze alarm should fire at `targetTime + snoozeDurationMinutes`, not `now + snoozeDurationMinutes`. Otherwise repeated snoozes push the user past their intended wake-up.
- **Why**: Today's snooze can drift; correctness issue.
- **Acceptance**: "SmartWake fires at -25min, user snoozes twice" ends within 18 minutes of the original target, not 18 minutes past it.
- **Status**: **Fixed**. Introduced `Constants.EXTRA_ORIGINAL_TARGET_MS` threaded through `AlarmReceiver` (sets `nowMs` — DEADLINE alarm fires at the target) and `SmartWakeService.fireAlarm` (sets stored `currentWindowEndMs`) into `AlarmPlaybackService.onStartCommand`. `snoozeAlarm()` now uses `anchor = max(originalTargetMs, nowMs)` then `anchor + snoozeMinutes` with `nowMs + 60s` safety floor. Notification Snooze action carries the extra too so it matches the in-app button.

### 11. Bedtime auto-detect

- **What**: A `BedtimeDetector` that watches screen-off + accelerometer-still + microphone-quiet for N consecutive minutes (default 15) and starts `SleepTrackingService` automatically. Disabled by default with a clear opt-in copy.
- **Why**: Matches the Sleep Monitor "no friction" pitch from the user's earlier comparison.
- **Acceptance**: A user who enables it and goes to bed without touching the app wakes up to a normal sleep report.
- **Notes**: Make the detection conservative — false-positive sessions (e.g. user sitting still watching TV) are worse than missed nights.
- **Status**: Open.

---

## Mic-based staging follow-ups

### 12. Signal-only recorder mode

- **What**: Lightweight mode of `AudioRecorderService` that runs the existing pipeline only as far as `MicSleepSignalAggregator` — no encoding, no Room writes for individual events. Suitable for users who want stage estimation but not snore review.
- **Why**: Saves storage and a tiny amount of CPU; lets the mic-staging toggle work even when the user wants no audio review.
- **Acceptance**: New `AudioRecorderService.ACTION_START_SIGNAL_ONLY` extra; settings explains the distinction.
- **Status**: Open.

### 13. Auto-start mic-staging when toggle is on

- **What**: When `mic_for_staging_enabled` is true and a sleep tracking session begins, automatically also start the recorder in signal-only mode. Currently the user has to remember to start both.
- **Why**: One toggle, one user expectation.
- **Acceptance**: Starting `SleepTrackingService` while the toggle is on results in mic-based stage estimates with no extra user action.
- **Status**: Open.

---

## Data rights & infrastructure

### 14. Server-side data-request processor

- **What**: A small service (Cloud Function / Cloud Run) that watches `/data_requests` Firestore collection. For `type: "export"` it bundles all docs under `/users/{uid}/...` into a downloadable zip and emails the user a signed URL. For `type: "delete"` it deletes the same documents (and the Firebase Auth account on confirmation), then marks the request `status: completed`.
- **Why**: The mobile side raises the request; without a processor, requests sit forever.
- **Acceptance**:
  - Export request fulfilled within 30 days (GDPR / DPDPA compliant).
  - Delete request fulfilled within 30 days with audit log of what was deleted.
  - Status visible to user in app via Firestore listener.
- **Dependencies**: `auth-and-sync` shipped (the Firestore collections exist).
- **Notes**: Out of repo scope but part of the product. Owner = backend / SRE.
- **Status**: Open.

### 15. APK size measurement under R8

- **What**: Build and measure the release APK with `isMinifyEnabled = true` + `isShrinkResources = true` after all the recent additions (Firebase, Health Connect, WorkManager, audio code). Compare with pre-Firebase baseline; identify large dependencies if size jumps significantly.
- **Why**: We promised "lighter app" as a goal; need to verify Firebase doesn't blow that up.
- **Acceptance**: APK size delta vs baseline documented in `docs/architecture.md`. R8 ProGuard rules updated if Firebase strips too aggressively.
- **Notes**: Expect ~3–4 MB increase from Firebase Auth + Firestore + Play Services Auth. ProGuard rules from each library are auto-included via their AAR consumer rules.
- **Status**: Open.

### 16. Automated test suite

- **What**: Unit tests for the pure logic that's easy to test in isolation, and a small Compose UI test suite for the critical screens.
- **Target priority list** (highest value first):
  - `SpectralClassifier` — fixture features per AudioEventType
  - `ProfileVoiceMatcher` — cosine + pitch similarity edge cases
  - `MotionOnlySleepStageEstimator` + `MultiSignalSleepStageEstimator` + `MicSleepStageEstimator`
  - `SleepCyclePredictor` — age-adjusted cycle math
  - `SmartWakeAnalyzer.decide` — all decision branches
  - `AlarmScheduler.calculateNextTriggerTime` — DST and day-of-week edge cases
  - `WearableSyncManager.syncRange` — empty source list, partial source failures, dedupe
  - `HealthConnectSource` — degraded mode when SDK missing
  - `VoiceMatcherFactory.create` — null profile / isolation flag combinations
  - Compose tests for onboarding flow, alarm editor, sleep result screen
- **Acceptance**: ≥ 50 unit tests + ≥ 5 Compose tests, all green in CI.
- **Notes**: Already flagged in `docs/issues.md` as the single largest open quality item.
- **Status**: **In progress**. First batch of unit tests landed (6 test classes, ~35 cases):
  - `SmartWakeAnalyzerTest` — wait / fire-now / deadline / boundary cases
  - `SleepQualityScorerTest` — bounds, interruption penalty, profile-driven adjustment
  - `SleepCyclePredictorTest` — age band, phase wrap, monotonic phase, stage bias
  - `SleepStageEstimatorFactoryTest` — vendor priority, motion-only, mic-only, empty fallback
  - `audio/classification/SpectralClassifierTest` — silence / cough / snore / talk / noise / unknown
  - `audio/isolation/VoiceMatcherTest` — factory wiring, matching/mismatch, empty profile
  Still pending: `WearableSyncManager`, `HealthConnectSource`, `AlarmScheduler.calculateNextTriggerTime` (private — needs extraction), Compose UI tests.

---

## Documentation

### 17. ADRs for the major decisions

- **What**: Architecture Decision Records under `docs/adr/` for the choices that would otherwise need to be re-litigated:
  - ADR-001: Why Strategy + Factory for estimators
  - ADR-002: Why on-device-first + opt-in cloud (not cloud-first)
  - ADR-003: Why Health Connect over direct vendor SDKs
  - ADR-004: Why Firebase over custom auth backend
  - ADR-005: Why we don't sync raw audio
  - ADR-006: Why the audio pipeline uses pooled FFT buffers
- **Why**: Onboarding new contributors / future-us.
- **Acceptance**: Six ADRs, each ≤ 1 page, follow the standard "context / decision / consequences" template.
- **Status**: Open.

### 18. End-user privacy notice

- **What**: A short user-facing privacy notice rendered inside the app (Settings → Privacy) that explains in plain language exactly what stays on device, what syncs to Firebase, and how to request export/deletion.
- **Why**: Required for India DPDPA and good practice everywhere.
- **Acceptance**: One-screen markdown rendered in the app; linked from the cloud-sync toggle copy.
- **Status**: Open.

---

## Bug backlog (open issues from in-flight agent work)

> These are recently reported by the user and currently being scheduled into the next agent dispatch. They'll move out of this file into `docs/issues.md` (Resolved) once fixed.

### 19. Voice isolation recording stalls at 100%

- **Symptom**: User taps Start Enrollment, progress bar fills to 5.0s / 5.0s, then nothing happens.
- **Root cause**: `OnboardingViewModel.startEnrollment` launched its collector on `AudioDispatchers.capture` — the same single-threaded dispatcher where `AudioRecordSource`'s producer runs via `flowOn(capture)`. The producer blocks in `AudioRecord.read()` and never yields, so the consumer never gets to update progress or persist the result.
- **Fix**: Changed `viewModelScope.launch(AudioDispatchers.capture)` to `viewModelScope.launch(Dispatchers.Default)`. Producer still runs on capture via `flowOn`, consumer runs on Default and can freely update state + write to Room.
- **Status**: **Fixed**.

### 20. Wearables step 4 of 6 — Health Connect not clickable

- **Symptom**: Health Connect card shows but the Connect button does nothing; no clear path to grant permissions.
- **Root cause**: The `permissionLauncher.launch(permissions)` was called with whatever set was cached via `requiredPermissions.ifEmpty { ... }` — on first composition this could still be empty, and `launch(emptySet())` is a silent no-op on Android.
- **Fix**:
  1. Connect button now re-fetches `viewModel.requiredPermissions()` synchronously inside the click handler before launching, so we never launch with an empty set.
  2. When the permission set is genuinely empty (Health Connect not exposing record types), a snackbar tells the user instead of silently doing nothing.
  3. Added an always-visible "Open Health Connect" button that fires `Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")` (falls back to Play Store) — guaranteed escape hatch even when our launcher fails.
  4. All intent dispatches wrapped in `runCatching` to handle missing resolvers.
- **Status**: **Fixed**.

### 21. App crashes when playing ambient sounds — **Fixed**.

### 22. App crashes on launch — **Fixed** (google-services plugin removed + SleepAnalyzerApp added).

### 23. Sleep Programs section appears empty

- **Symptom**: Programs screen shows an empty list even though `ProgramsViewModel.init` had seeding logic.
- **Root cause**: Seeding only fired when ProgramsViewModel was first instantiated. After a destructive Room migration (or if the user never opened the Programs screen first), the table stayed empty forever.
- **Fix**:
  1. Extracted the seed catalog into `data/seed/ProgramSeeds.kt` so adding/editing programs is a data-only change.
  2. `SleepAnalyzerApp.onCreate` now seeds programs on first run gated by a new `AppPreferences.programsSeededFlow` flag.
  3. Seeding also re-runs if the flag is set but the table is empty (defends against destructive migrations).
  4. De-duplicates by title so re-seeding is idempotent.
- **Status**: **Fixed**.

### 24. Recording does nothing / sleep states never categorized

- **Symptoms**: Tapping Start on Recorder did nothing; SleepResult showed 0 minutes for every stage.
- **Root causes fixed in this pass**:
  1. **Permission-grant tap-twice UX**: After the user granted RECORD_AUDIO, the start path didn't auto-resume — user had to tap Start again. Many users gave up at the first tap.
  2. **Foreground-service-type mismatch**: `SleepTrackingService` and `SoundPlayerService` were calling untyped `startForeground(id, notification)` instead of `ServiceCompat.startForeground(this, id, notification, FOREGROUND_SERVICE_TYPE_*)`. On Android 14+ this can throw `MissingForegroundServiceTypeException` even when the manifest declares the type.
  3. **No user feedback** on start/stop — silent failures were indistinguishable from "service started but nothing happened".
- **Fix**:
  1. `RecorderScreen` and `SleepTrackerScreen` now track `awaitingPermissionForStart`; a `LaunchedEffect` keyed on `(allPermissionsGranted, awaitingPermissionForStart)` auto-invokes `startRecording()` / `startTracking()` once permissions become granted.
  2. Migrated `SleepTrackingService.startForeground` and `SoundPlayerService.startForeground` to `ServiceCompat.startForeground` with `FOREGROUND_SERVICE_TYPE_HEALTH` and `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` respectively. `AudioRecorderService` and `AlarmPlaybackService` and `SmartWakeService` already had typed calls; verified all five services are now correct.
  3. Added "Recording started" / "Tracking started" / "Recording stopped" toasts so every tap produces immediate visible feedback.
- **Status**: **Fixed**.

### 25. Defensive guard on HeartRateCard

- **Symptom**: Theoretical crash if `HeartRateCard` is ever called with an empty `List<WearableSample>`. Caller already gates with `if (heartRateSamples.isNotEmpty())`, but a future caller could forget.
- **Fix**: Added `if (samples.isEmpty()) return` at the top of `HeartRateCard` so the component is safe in all call sites.
- **Status**: **Fixed**.

### 26. Recording playback MediaPlayer leak

- **Symptom**: Repeatedly playing/stopping saved recordings could leak `MediaPlayer` native resources if `stop()` was called from an invalid player state.
- **Root cause**: `RecorderViewModel.stopPlayback()` called `mediaPlayer?.stop()` before `mediaPlayer?.release()` without `try/finally`. `MediaPlayer.stop()` can throw `IllegalStateException`; when it did, release/nulling never happened.
- **Fix**: Capture the player in a local variable, wrap `stop()` in `try/catch`, and always call `release()` in `finally` before clearing `_playingRecordingId`.
- **Status**: **Fixed**.

---

## Changelog

- 2026-05-27 Issue fixes completion: **#9** profile-driven scoring weights — extracted `SleepQualityScorer` as a pure object that adapts deep-sleep target and ideal duration range to `UserProfile.activityLevel`; **#10** smart-wake snooze logic — `Constants.EXTRA_ORIGINAL_TARGET_MS` threaded through `AlarmReceiver` / `SmartWakeService` → `AlarmPlaybackService` so snooze anchors to `max(originalTarget, now) + snoozeMinutes` instead of always `now + snoozeMinutes`; **#16** first wave of automated unit tests (~35 cases across `SmartWakeAnalyzer`, `SleepQualityScorer`, `SleepCyclePredictor`, `SleepStageEstimatorFactory`, `SpectralClassifier`, `VoiceMatcher`).
- 2026-05-26 Implemented backlog: **#19** (voice enrollment dispatcher), **#20** (wearables HC button + Open-Health-Connect escape hatch + safe intents), **#23** (programs seeded from `SleepAnalyzerApp` gated by `programsSeededFlow`), **#24** (auto-resume start after permission grant in Recorder/Tracker, all 5 services migrated to `ServiceCompat.startForeground` with typed flag, user-facing toasts on every start/stop), **#25** (defensive `if (samples.isEmpty()) return` inside `HeartRateCard`), **#26** (`MediaPlayer.release()` now guaranteed in recorder playback). All fixes verified by `assembleDebug`.
- 2026-05-26 #22 fixed: removed `google-services` Gradle plugin (Firebase auto-init via placeholder config was killing the process on launch); added `SleepAnalyzerApp` with a default uncaught-exception handler that writes full stack traces to `filesDir/crash_log/last_crash.txt`; hardened `MainActivity.onCreate` with `runCatching` around DataStore, WorkManager, and CloudSyncScheduler calls.
- 2026-05-26 #21 fixed end-to-end: manifest now has `foregroundServiceType="mediaPlayback"` for `SoundPlayerService`, AND `SoundGeneratorFactory` now maps every distinct sound id to a unique procedural preset.
- 2026-05-26 Initial backlog created.
