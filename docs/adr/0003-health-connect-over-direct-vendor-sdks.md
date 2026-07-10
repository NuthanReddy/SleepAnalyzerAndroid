# ADR-0003: Health Connect as the primary wearable source

- **Status**: Accepted
- **Date**: 2026-07-10

## Context

Users wear Fitbit, Pixel Watch, Galaxy Watch, Oura, Garmin, Whoop, and more. Each vendor ships its
own SDK/Web API with its own OAuth, rate limits, data model, and review process. Integrating even two
or three of them directly is a large, ongoing maintenance burden, and most modern wearables already
write heart rate, HRV, respiration, SpO2, and full sleep-stage segments back to Android's Health
Connect. We want the widest device coverage for the least integration surface, while keeping the door
open to add a direct vendor source where Health Connect coverage is genuinely poor.

## Decision

We treat **Health Connect as the primary wearable source**. `HealthConnectSource` implements a small
`WearableSource` interface and is registered in a `SourceRegistry`; the rest of the app depends only
on that interface, never on Health Connect (or any vendor) types directly. Vendor stage segments read
from Health Connect are mapped into our own `SleepStage` enum via the pure `HealthConnectStageMapper`,
so downstream code (estimators, UI) stays vendor-neutral. Direct vendor integrations remain possible
as *additional* `WearableSource` implementations behind the same interface.

## Consequences

- **Positive**: One integration unlocks every wearable that writes to Health Connect — including HR,
  HRV, respiration, SpO2, and PSG-grade vendor stages we could never compute ourselves.
- **Positive**: The `WearableSource` seam keeps vendor specifics out of the domain; adding Samsung
  Health or a Fitbit Web source later is additive, not invasive.
- **Negative / trade-offs**: We inherit Health Connect's availability gaps — some devices/OS versions
  don't write certain record types, and permission UX is Health-Connect-shaped (see fixed bug #20). We
  depend on an SDK that gates newer metrics (e.g. skin temperature) behind alpha releases (backlog #8).
- **Follow-ups**: Direct `SamsungHealthSource` (#4) and `FitbitWebSource` (#5) are backlog items to
  fill coverage gaps; additional HC metrics are #7.
