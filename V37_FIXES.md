# Localis v2.1.0 UI / Runtime Fixes

## This revision
- Replaced the custom Material 3 loading canvas with the official AndroidX Material 3 `CircularProgressIndicator`.
- Reduced chat recomposition pressure by batching streamed tokens before updating Compose state.
- Prevented concurrent native generations, reducing intermittent send-time crashes.
- Made GGUF download progress emit immediately and update at 100ms intervals so progress visibly moves.
- Reworked the home AI visual from a hard sphere into a diffuse light field with free-floating colored particles.
- Reduced composer height/elevation and removed visual line-like artifacts.
- Fixed the model-selection action text clipping by increasing touch target height and constraining text.
- Removed model-center "Recommended" / "Try it" chips. Only memory-risk states remain; both "Memory tight" and "Insufficient memory" use the Material 3 error container (red) style.
- Kept model name and author on one line with a middle dot, preventing the previous two-row appearance.
- Localized model likes and search-round status.
- Audited all 9 non-default locale resource sets: all contain the same 486 string keys.

## Locales checked
Arabic, German, French, Japanese, Korean, Portuguese, Russian, Simplified Chinese, Traditional Chinese, plus the English base resources.

## Release build
The existing GitHub Actions release workflow remains enabled. It installs Android API 35, Build Tools 35.0.0, CMake 3.22.1 and NDK 27.2.12479018, runs the release audit, builds a signed release APK, verifies the APK signature and publishes it to a GitHub Release.

## Verification performed here
- Release audit: PASS
- JNI native method audit: 15/15
- Locale key audit: PASS, 486 keys in every locale
- ZIP integrity: checked after packaging

A full Android/NDK Gradle build still requires the GitHub runner/toolchain because this environment does not have the required Gradle/Android SDK caches available offline.
