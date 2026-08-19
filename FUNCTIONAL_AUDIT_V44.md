# Localis v2.1.0 — Functional Audit v44

## Fixed in this revision

- Onboarding flash: the first frame now waits for persisted settings and saved locale to load, so the onboarding page cannot briefly appear and disappear on every launch.
- Model loading UI: import/loading operations share one status area; two simultaneous progress indicators are no longer rendered.
- Model loading safety: a memory preflight rejects unsafe loads before native allocation and catches Kotlin `OutOfMemoryError` with a user-facing failure instead of continuing with a half-loaded engine.
- Chat history import/export: the Data screen now uses Android document pickers and persists a versioned JSON export. Import merges by conversation ID and accepts both the new full export and legacy single-conversation JSON.
- Tor state: the switch is now driven by the actual `TorManager.Status.ON` state. A requested/persisted setting is not displayed as connected while Tor is still STARTING or has failed.
- Bridge editing is disabled while Tor is STARTING/ON.

## Important Tor audit result

The current embedded Tor implementation is **an app-level SOCKS route**, not an Android system VPN. `TorManager` starts the embedded Tor service, waits for a reachable SOCKS port, and routes the app's OkHttp traffic through that SOCKS endpoint. It does **not** call `VpnService` or establish a TUN interface.

This revision intentionally does not fake a VPN state. The UI wording was corrected so `Tor connected` does not claim system-wide VPN routing. A real Android VPN requires `VpnService` plus a TUN-to-SOCKS forwarding engine (for example a tun2socks backend). That backend is not currently vendored into this source tree, so claiming that the current build is a true VPN would be incorrect.

## Build validation

- Language audit: 504 keys across 10 locales — PASS
- CI preflight: PASS
- Release audit: PASS
- JNI declaration/implementation audit: 18/18 — PASS
- Full Gradle compile was attempted but cannot run in this environment because Gradle 8.9 is not cached and outbound DNS access is unavailable.
