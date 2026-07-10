# ADR-0006: Pooled FFT buffers in the audio pipeline

- **Status**: Accepted
- **Date**: 2026-07-10

## Context

Because all audio analysis runs on-device (ADR-0005), the overnight pipeline computes FFT magnitude
spectra continuously for hours — spectral classification (snore/cough/talk), breathing extraction,
and movement-burst detection all consume the frequency domain. A naive FFT that allocates fresh
`re`/`im` scratch arrays per frame would churn the heap every few milliseconds, triggering GC pauses
that add capture jitter and drain battery on exactly the low-cost devices we target ("lighter app").

## Decision

We use a hand-rolled radix-2 Cooley–Tukey `FftProcessor` with **reusable buffer pooling** and
single-precision floats. Precomputed `cos`/`sin` twiddle tables and a bit-reversal table are built
once per instance. `magnitudeSpectrum` borrows a `(re, im)` scratch pair from a `ConcurrentLinkedDeque`
pool (`borrowBuffers` / `recycleBuffers`, capped at 4) and always recycles it in a `finally`, so the
steady state performs **zero per-frame scratch allocation**. Processors share one instance per FFT
size via `FftProcessor.forSize(size)` (a `ConcurrentHashMap` cache), and the pipeline itself runs on a
dedicated `AudioDispatchers.processing` dispatcher with Flow conflation for backpressure.

## Consequences

- **Positive**: Hot-path allocation and GC pressure drop to near zero during long recordings, keeping
  capture jitter low and battery use down; the pool and instance cache are thread-safe for the
  multi-consumer pipeline.
- **Positive**: No third-party FFT/native dependency — smaller APK, no JNI, fully unit-testable JVM
  code.
- **Negative / trade-offs**: We own and must maintain/verify our own FFT (correctness rests on the
  test suite, e.g. `SpectralClassifierTest`), forgoing the extra speed of a vectorized native library.
  Pooling adds a small amount of lifecycle complexity (borrow/recycle discipline) that callers must not
  bypass.
- **Follow-ups**: If profiling ever shows the pure-Kotlin FFT is the bottleneck, a native/vectorized
  backend could replace `FftProcessor` behind the same API without touching callers.
