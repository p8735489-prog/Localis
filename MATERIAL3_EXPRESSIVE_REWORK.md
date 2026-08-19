# Material 3 / Expressive rework — v40

- UI uses AndroidX Compose Material 3 components directly.
- Android 12+ dynamic color uses dynamicLightColorScheme/dynamicDarkColorScheme (Monet).
- Loading states call CircularProgressIndicator directly; the custom loading wrapper was removed.
- Settings shapes are tightened to avoid CJK clipping and oversized corners.
- Tor routing disables the chat composer while STARTING/ON and explains why. Bridge editing is disabled while Tor is active and the UI explains how to change it.
- Thread controls are explicitly labeled as download threads.

The stable AndroidX Material 3 release is 1.4.0 as of August 2026; Material 3 Expressive APIs are being introduced in the 1.5 alpha line. The app therefore uses the stable Material 3 component set directly rather than depending on an unrelated Wear-only Expressive artifact.
