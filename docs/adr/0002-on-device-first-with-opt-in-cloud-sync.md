# ADR-0002: On-device-first persistence with opt-in cloud sync

- **Status**: Accepted
- **Date**: 2026-07-10

## Context

Sleep data is intimate — audio, biometrics, and habits. Users in our target markets (incl. India,
under DPDPA) expect the app to work fully offline and to treat the cloud as a bonus, not a
precondition. At the same time, signed-in users want their history to survive a reinstall or a new
device, and the future ML corpus (backlog #1) depends on an opt-in upload path existing. The app must
therefore run with no network and no Firebase project configured at all, yet light up sync cleanly
when Firebase is present and the user opts in.

## Decision

We make **on-device storage the source of truth**: Room (`SleepRepository`) plus DataStore hold all
core data, and there are **no required network dependencies**. Cloud sync is an **opt-in overlay**:
`AuthRepository` and `SyncRepository` sit on top of the local store and activate only when Firebase is
configured and the user signs in and enables sync. When Firebase is absent or still holds placeholder
config, these layers short-circuit with `firebaseNotConfiguredException` / `isPlaceholderFirebase`
instead of crashing, and the app continues as a guest. Sync is one-directional snapshot/incremental
upload (`uploadFullSnapshot` / `uploadIncremental`) driven by a WorkManager worker
(`CloudSyncWorker` + `CloudSyncScheduler`).

## Consequences

- **Positive**: The app installs and runs with zero backend setup; contributors don't need Firebase
  credentials to build or use it. Privacy story is simple to explain and enforce.
- **Positive**: Auth/sync failures are non-fatal by construction — the local experience never depends
  on the network.
- **Negative / trade-offs**: Two persistence surfaces (local canonical + cloud mirror) mean upload
  mapping code (entity → Firestore doc) must track schema changes. Sync is currently upload-only;
  multi-device *download*/merge is not yet solved.
- **Follow-ups**: Server-side data-request (export/delete) processing is out of app scope (backlog
  #14); the training-corpus upload path reuses this opt-in overlay (backlog #1).
