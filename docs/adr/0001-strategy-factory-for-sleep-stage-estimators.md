# ADR-0001: Strategy + Factory for sleep-stage estimators

- **Status**: Accepted
- **Date**: 2026-07-10

## Context

Predicting the current sleep stage (AWAKE / LIGHT / DEEP / REM) draws on wildly different inputs
depending on what the user has: sometimes only phone-accelerometer motion, sometimes wearable
HR/HRV/respiration, sometimes microphone-derived breathing features, and sometimes fully-formed
vendor stage segments imported through Health Connect. We also expect to add a TFLite/ONNX model
path later (see backlog #1–#3). We need runtime selection of the best available approach without
callers knowing the details, and every approach must be cheap to unit-test in isolation.

## Decision

We model stage estimation as a **Strategy** behind the `SleepStageEstimator` functional interface —
`fun estimate(signals: SleepSignals): SleepStageEstimate` — with one implementation per signal
regime: `VendorSleepStageEstimator`, `MultiSignalSleepStageEstimator`, `MicSleepStageEstimator`, and
`MotionOnlySleepStageEstimator`. `SleepSignals` bundles every optional input; each estimator reads
only what it needs and degrades to a `fallback` estimator when its preferred signal is absent.

`SleepStageEstimatorFactory.create(signals)` is the **Factory** that picks the strategy by a fixed
priority: vendor segments → multi-signal (HR/HRV present) → mic-only → motion-only, with motion-only
as the universal low-confidence default. Estimators are pure functions with no Android dependencies
and are cheap to construct, so the factory does not cache them.

## Consequences

- **Positive**: Adding the future ML estimator is a new `SleepStageEstimator` plus one branch in the
  factory — no caller changes. Purity makes the whole family JVM-unit-testable; the suite covers each
  implementation directly plus the factory's selection logic.
- **Positive**: Graceful degradation is structural (the `fallback` chain), not scattered `if`s.
- **Negative / trade-offs**: Selection lives in one factory `when` that must be kept in sync as new
  signals appear; priority ordering is a hand-tuned heuristic rather than a learned policy.
- **Follow-ups**: The heuristic scoring inside each estimator is explicitly "replaceable by a model
  later" — the ML path (backlog #1–#3) slots in without disturbing this structure.
