# Localis v38 fixes

- Fixed three missing `R.string` resources used by model/vision screens: `refresh`, `model_error_status`, `model_loaded_ready`.
- Added missing `com.localaisearch.R` import to `SourceCard.kt`.
- Added `tools/ci_preflight.py` and wired it into GitHub Actions before release compilation. It audits locale string parity, Kotlin resource references, JNI declarations, imports and delimiter balance.
- History is visually separated into pinned and recent sections with localized section headers and dividers.
- Settings hub and AI/model settings use a more consistent Pixel-style Material 3 surface hierarchy: low-contrast containers, compact rows, tonal icon containers and consistent spacing.
- Preserved the v36 GitHub signing/build workflow.

Local environment limitation: Gradle 8.9 cannot be downloaded here because external DNS/network access is unavailable, so an actual Android Release compile cannot be claimed from this environment.
