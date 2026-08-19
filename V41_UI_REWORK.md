# v41 UI rework

- Removed the grey backing card behind the home light field.
- Rebuilt AIOrb as an ambient Material 3 themed light field with free particles, breathing glow and loading arcs.
- Model loading now visibly animates the light field and uses the native Material 3 CircularProgressIndicator.
- Added reusable ExpressiveCard using AndroidX Material 3 Card APIs and MaterialTheme shapes/colors/elevation.
- Reworked the chat composer to sit directly above navigation gestures and move with IME insets.
- Removed the extra small model-status text under the composer.
- Reduced composer height and removed the previous custom white-line/indicator feel.
- Kept versionName at 2.1.0.
