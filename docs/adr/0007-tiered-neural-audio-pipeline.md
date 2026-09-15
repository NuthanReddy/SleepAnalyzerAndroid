# ADR-0007: Tiered neural audio pipeline over a linear one

- **Status**: Proposed
- **Date**: 2026-08-09

## Context

Sleep-sound classification is currently a hand-tuned heuristic: `AudioFeatures` reduces a cropped
event to a `FeatureVector` (8 scalars + 13 band energies) and `SpectralClassifier` applies four
threshold rules to choose `SNORE` / `COUGH` / `TALK` / `NOISE`. It is small, fast, and unit-tested,
but the thresholds are magic numbers, cough detection conflates any loud broadband transient with a
cough, and there is no path to improvement other than retuning constants by hand.

A replacement was proposed as a linear chain: *raw audio → DeepFilterNet3/RNNoise → pyannote VAD →
AST/PANNs → {snore, cough, other} → Whisper on speech → sleep-talking*. The stage decomposition is
sound, but adopted literally on Android it fails on four counts:

1. **pyannote VAD is a speech detector.** As a serial gate it would discard snoring and coughing —
   the pipeline's own headline outputs — before they reach the classifier. It also regresses the
   deliberate "VAD is observed, not applied" property of the current pipeline (§7).
2. **Continuous inference is untenable.** An 8-hour night is 28,800 s of audio; running a denoiser,
   a segmenter, and an AST/PANNs-class transformer continuously plausibly costs **2.4–6.8 hours of
   sustained single-core CPU per night**, against a codebase whose "lighter app" goal (§5) justifies
   a hand-rolled FFT purely to avoid GC churn.
3. **Footprint.** Those four models total ~380–425 MB against a **3.38 MB** release APK that today
   bundles 0.06 MB of native libraries. `pyannote` is additionally gated on Hugging Face and has no
   supported Android runtime path.
4. **Whisper collides with our privacy contract.** ADR-0005 keeps raw audio on-device, and
   signal-only recording retains *nothing*. Transcription converts transient bedroom speech — of the
   user and of non-consenting bystanders — into durable, searchable text.

A follow-up task-by-task model survey proposed splitting the classifier further (PANNs-CNN14 for
cough, snore-fine-tuned AST) and raising Whisper to Small/Turbo, while conceding that edge/mobile
deployment implies Silero VAD + RNNoise. Because ADR-0005 leaves this app with **no server tier for
audio**, that mobile row is the only one that applies. The survey's per-task split also shipped
~630 MB and three forward passes for labels that YAMNet's published AudioSet class map already
emits in one.

## Decision

We adopt the **intent** (learned models replacing thresholds; a separate speech path) and reject the
**topology**, restructuring it as a **tiered cascade** in which each tier runs only on what the tier
below flagged. Full design: [`docs/neural-audio-pipeline.md`](../neural-audio-pipeline.md).

- **Tier 0 — activity gate (100% of frames, ~free)**: the existing DSP path (`BandPassFilter`,
  `AudioMath` RMS/ZCR, adaptive noise floor, `ShortRingBuffer`, `AudioCropper`) is retained
  unchanged and is the only always-on stage. Segmentation is decoupled from *speech* detection.
- **Tier 1 — event tagging (candidate windows only)**: **YAMNet** (~4 MB, Apache-2.0, AudioSet 521
  classes) instead of AST/PANNs-CNN14 (~330 MB). Its published class map natively contains every
  label we need — `Snoring` (38), `Cough` (42), `Sneeze` (44), `Breathing` (36), `Gasp` (39),
  `Snort` (41), `Speech` (0) — so a per-task split (AST for snore, CNN14 for cough, YAMNet for
  general) would ship ~630 MB and pay three forward passes for labels **one ~4 MB model produces in
  one pass**. An **episode rate limiter** collapses long snore runs to 1-in-K sampling so habitual
  snorers cannot defeat the gate.
- **Tier 2 — speech confirmation (speech-tagged windows only)**: **Silero VAD** (~1.8 MB, MIT, ONNX)
  instead of `pyannote`, and positioned as a **branch fed by the classifier's `Speech` label**, never
  as a gate ahead of it.
- **Tier 3 — transcription (opt-in, rare)**: Whisper-**tiny.en** int8 (~40 MB), on-device,
  **default off**, gated behind a second consent separate from sleep-talk detection. Explicitly
  *not* `small` (244 M params, ~2 GB VRAM) or `turbo` (809 M, ~6 GB): neither is deployable in an
  all-night Android foreground service, and sleep-talk transcription fails *acoustically* rather
  than linguistically, so a larger decoder mostly makes the model more fluent at guessing.
- **Denoising is deferred** *in the always-on path*. DeepFilterNet3/RNNoise optimise for *speech*
  targets and may attenuate snore/cough — the signals we most need. `BandPassFilter` stays; RNNoise is
  revisited only if Tier-1 evaluation shows noise-driven errors, and only as a Tier-1 pre-step on
  candidate windows. Note that YAMNet **labels** the relevant noise sources directly —
  `Mechanical fan` (406), `Air conditioning` (407), `Mains hum` (510) — so fan/AC can be recognised as
  context and rejected as a confounder instead of being filtered out of the signal.
- **The heavyweight stack is relocated, not discarded.** DeepFilterNet3 + AST/PANNs are appropriate
  for a **post-session deep pass** over retained clips, run only while charging (backlog #31). Batched,
  bounded, mains-powered work removes the RTF/thermal/battery objections that make them untenable in a
  continuous 8 h foreground service. That pass cannot improve Tier-0 recall, which remains the
  system-wide ceiling — retaining a full night is not an option at ~1.27 GB.
- **Models ship on demand**, not in the APK, keeping the base APK at ~3.4 MB.
- **Code shape**: a new `PcmClassifier` interface (neural models need the waveform, not a lossy
  `FeatureVector`) alongside the existing `AudioClassifier`; `ClassifierFactory` gains
  `Backend.NEURAL` and falls back to `SPECTRAL` whenever models, capability, or consent are absent.
- **ADR-0005 is extended**: *text derived from audio is treated as raw audio.* Transcripts are
  excluded from sync payloads by document **shape**, not by a settings check.

## Consequences

- **Positive**: Expensive inference is bounded by duty cycle rather than wall-clock, so the overnight
  battery/thermal budget survives; cough, sneeze, gasp, and talk detection improve substantially over
  threshold rules; the base APK is unchanged; every feature degrades to `SpectralClassifier` when
  models are missing, so nothing hard-depends on a download.
- **Positive**: Mic-based staging (§7c) and signal-only zero-retention recording are untouched and
  need no models at all — the strongest privacy mode gets no weaker.
- **Negative / trade-offs**: We accept ~46 MB of downloadable models plus a delivery, verification,
  and storage-management surface we did not previously have; an interpreter lifecycle to own; two
  classifier abstractions coexisting during migration; and `AudioRecording` gains an additive Room
  migration for backend/model provenance. We also accept that sleep-talk **transcription** may never
  ship — measured WER on slurred, distant, low-SNR speech may not justify it, in which case
  detection-only is the terminal state.
- **Negative / trade-offs**: The RTF and model-size figures driving this decision are estimates. They
  gate Phase 0 (a benchmark harness) precisely because the decision to tier — though robust across
  the plausible range — deserves measurement before implementation.
- **Follow-ups**: backlog **#27** (benchmark harness + labelled eval set), **#28** (Tier-1 YAMNet
  behind a default-off flag), **#29** (Tier-2 sleep-talk detection, metadata only), **#30** (Tier-3
  transcription, separate opt-in + retention limits), **#31** (optional Tier-4 offline deep pass).
  No code changes until #27 reports.
