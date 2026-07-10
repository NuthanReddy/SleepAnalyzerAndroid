# ADR-0005: Never sync raw audio — metadata only

- **Status**: Accepted
- **Date**: 2026-07-10

## Context

To classify snoring, coughing, sleep-talking, and breathing, the app records microphone audio
overnight. That raw audio is the single most sensitive artifact the app produces — it can capture
bedroom conversations and a partner's voice. Cloud sync (ADR-0002) is valuable for durability and for
the future ML corpus, but uploading raw audio would create disproportionate privacy and compliance
risk (DPDPA/GDPR) and a large bandwidth/storage cost, for little user benefit.

## Decision

**Raw audio never leaves the device.** Recorded audio files stay in app-private storage on the phone;
cloud sync uploads only **derived metadata**. `SyncRepository.uploadFullSnapshot` /
`uploadIncremental` write the user profile, active goal, daily summaries, sleep sessions, per-event
**audio-event metadata** (`audioEventDoc` — type, timing, and classification, not the waveform), and
notes. The Settings sync copy states this contract explicitly to the user: *"Sync profile, goals,
summaries, sessions, audio-event metadata, and notes. Raw audio never leaves the device."* Any future
ML training upload is limited to derived **features/labels**, not audio (backlog #1).

## Consequences

- **Positive**: The most sensitive data has the smallest possible blast radius; the privacy promise is
  simple, enforceable, and visible in-product. Sync payloads stay small.
- **Positive**: Reduces regulatory surface — no raw voice recordings in the cloud to export/delete.
- **Negative / trade-offs**: Audio review is device-local only; a user who reinstalls loses their raw
  recordings (summaries/metadata still restore). Server-side re-analysis of audio is impossible by
  design — all audio analysis must run on-device (see the audio pipeline, ADR-0006).
- **Follow-ups**: The user-facing privacy notice (backlog #18) should state this guarantee verbatim;
  the training corpus (backlog #1) must gate on features-only uploads with explicit consent.
