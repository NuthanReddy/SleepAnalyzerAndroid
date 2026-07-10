# ADR-0004: Firebase Authentication over a custom auth backend

- **Status**: Accepted
- **Date**: 2026-07-10

## Context

Cloud sync (ADR-0002) needs a stable per-user identity to key Firestore documents, and users expect
familiar sign-in options (Google, phone/OTP). Building and operating our own auth backend — secure
credential storage, OTP delivery, session/token management, account recovery, provider integrations —
is a large amount of undifferentiated, security-critical work for a solo/small team. Because sync is
optional, auth must also be entirely absent-friendly: the app cannot require an auth service to exist.

## Decision

We use **Firebase Authentication** as the identity provider, wrapped by `AuthRepository`. It supports
Google and phone providers, exposes auth state as a `StateFlow<AuthState>`, and mirrors the signed-in
user into a local `UserAccount` for offline reads. A Firebase `AuthStateListener` reconciles state:
sign-out clears the local account and returns to `AuthState.Guest`; sign-in persists and emits the
user, but only when Firebase `isConfigured()`. The Firebase user's UID becomes the Firestore document
key that `SyncRepository` writes under.

## Consequences

- **Positive**: We get Google + phone auth, token refresh, and account lifecycle for near-zero backend
  code, and a UID that pairs naturally with Firestore security rules.
- **Positive**: Guest-first fallback is built in — no Firebase project means the app runs as a guest
  rather than failing.
- **Negative / trade-offs**: Vendor lock-in to Firebase/GCP; the auth UX is constrained by what the
  Firebase SDK offers. Play Services Auth + Firebase Auth add to APK size (see #15). A misconfigured
  `google-services` setup previously caused a launch crash (fixed bug #22), so init is now guarded.
- **Follow-ups**: Account **deletion** (and data export) needs the server-side processor in backlog
  #14 to actually purge cloud data on request.
