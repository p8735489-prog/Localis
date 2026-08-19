# Localis v48 — full bug & interaction audit

## Critical build/CI findings fixed

1. `app/src/main/cpp/CMakeLists.txt` did not load llama.cpp's CMake helper before `common/`, causing:
   `Unknown CMake command "llama_add_compile_flags"`.
   A bundled `app/src/main/cpp/cmake/common.cmake` compatibility helper is now loaded before `common/`.
2. A second GitHub workflow contained a committed Base64 keystore and hard-coded signing passwords. The duplicate workflow was removed; the canonical workflow uses GitHub Actions Secrets only.
3. `keystore_base64.txt` was removed from the project package. Because signing material was present in repository history, rotate the release keystore before a real public release.
4. `ci_preflight.py` now detects duplicate/extra/missing resource keys, hardcoded UI strings, JNI mismatches, and missing native CMake helper wiring.
5. `release_audit.py` now detects hard-coded signing material, duplicate release workflows, and missing llama CMake helper wiring.

## Interaction bugs fixed

- Settings > Data > Clear chat was previously a no-op; it now deletes stored conversations.
- Clear cache was previously a no-op; it now removes the app cache directory.
- Reset App was previously a no-op; it now clears conversations, cache, and DataStore settings, then recreates the Activity.
- The custom Canvas loading animation wrapper was replaced with the AndroidX Material 3 Expressive `LoadingIndicator` component.
- Duplicate `search_round` resource definitions were removed from all locales.
- Auto Mode accessibility text is now localized.

## Important risks still requiring device/CI verification

### llama.cpp / mtmd version coupling
The repository bundles a llama.cpp source snapshot but does not bundle `tools/mtmd`; CI downloads `mtmd` from `master`. This is not reproducible and can break when `master` changes APIs. Pin mtmd to the exact llama.cpp commit used by the bundled source before the release build is considered reproducible.

### Native crash verification
Static JNI and CMake checks pass, but an Android device/emulator run is still required to validate:
- GGUF load/unload under memory pressure
- first-generation stability
- rapid send/cancel/model-switch races
- Qwen/Qwen3 chat-template output
- matching `mmproj` + mtmd vision inference
- Tor start/stop while network requests are active

### Tor semantics
Current Tor routing is app-only SOCKS routing through OkHttp. It is intentionally not an Android system VPN/TUN. UI should continue to call it "Tor only for this app" and only show connected after the local SOCKS listener is reachable.

## Verification performed in this environment

- ZIP/source integrity: passed
- Python CI preflight: passed
- Release audit: passed
- Locale key set: 10 locales, no missing/extra/duplicate keys after cleanup
- JNI declaration audit: 18/18
- Gradle compile: not executable here because Gradle 8.9 is not cached and this environment cannot resolve `services.gradle.org`.
