# Localis v46 — Tor/Proxy + Material 3 Expressive rework

- Added a dedicated Settings > Tor & Proxy entry.
- Separated app-only Tor routing from manual proxy settings.
- Tor status remains OFF/STARTING/ON/ERROR; UI never treats STARTING as connected.
- Manual bridge editing is disabled while Tor is starting/connected.
- The Tor screen explicitly states that this is app-only SOCKS routing, not Android system-wide VPN.
- Upgraded Compose Material 3 dependency to Google AndroidX `1.5.0-alpha25` to use native Material 3 Expressive APIs.
- Tor connection loading uses the native Material 3 Expressive `LoadingIndicator`, not a custom Canvas spinner.
- Kept MaterialTheme shapes/colors and reduced custom styling.
- Added localized strings for all 10 locales.
