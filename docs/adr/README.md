# Architecture Decision Records

This directory captures the significant, hard-to-reverse decisions behind Sleep Analyzer — the ones
that would otherwise get silently re-litigated by future contributors (or future us). Each record is
short (≤ 1 page) and follows a fixed template:

- **Status** — Proposed / Accepted / Superseded (with a link to the superseding ADR)
- **Context** — the forces at play: requirements, constraints, and what made the choice non-obvious
- **Decision** — what we chose, in the present tense ("We use …")
- **Consequences** — what becomes easier, what becomes harder, and what we accept as a trade-off

These are numbered, immutable once Accepted, and never deleted. If a decision changes we add a new
ADR that supersedes the old one rather than editing history.

> Note: ADR-0001 … ADR-0006 were written retroactively on 2026-07-10 to document decisions already
> embodied in the shipped code (see backlog item #17). Dates reflect when each was recorded, not the
> original commit.

## Index

| ADR | Title | Status |
| --- | --- | --- |
| [0001](0001-strategy-factory-for-sleep-stage-estimators.md) | Strategy + Factory for sleep-stage estimators | Accepted |
| [0002](0002-on-device-first-with-opt-in-cloud-sync.md) | On-device-first persistence with opt-in cloud sync | Accepted |
| [0003](0003-health-connect-over-direct-vendor-sdks.md) | Health Connect as the primary wearable source | Accepted |
| [0004](0004-firebase-over-a-custom-auth-backend.md) | Firebase Authentication over a custom auth backend | Accepted |
| [0005](0005-never-sync-raw-audio.md) | Never sync raw audio — metadata only | Accepted |
| [0006](0006-pooled-fft-buffers-in-the-audio-pipeline.md) | Pooled FFT buffers in the audio pipeline | Accepted |

## Template

Copy [`_template.md`](_template.md) when adding a new ADR. Pick the next free number, keep it to a
page, and add a row to the index above.
