# v40 Material 3 / Tor routing rework

- Replaced the custom loading wrapper with direct AndroidX Material 3 `CircularProgressIndicator` calls.
- Settings use Material 3 theme shape tokens instead of ad-hoc oversized radii.
- Tor STARTING/ON disables the chat composer; the UI explains this state.
- Bridge editing is disabled while Tor is active and the UI tells the user to turn Tor off before changing the bridge.
- Thread controls are explicitly labeled as **download threads**, with a description of what they control.
- Android 12+ dynamic color remains enabled through `dynamicLightColorScheme` / `dynamicDarkColorScheme`.
- Material 3 dependency is pinned to the stable AndroidX Material 3 1.4.0 release.
