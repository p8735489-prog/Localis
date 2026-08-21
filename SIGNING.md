# Localis Release Signing

Localis release APKs are signed by GitHub Actions with the project's release keystore supplied through GitHub Actions Secrets.

Required repository secrets:

- `SIGNING_KEYSTORE_B64` — Base64 of the release `.jks` file
- `SIGNING_STORE_PASSWORD` — keystore password
- `SIGNING_KEY_ALIAS` — alias inside the keystore
- `SIGNING_KEY_PASSWORD` — key password

The keystore itself must never be committed to the repository or included in an APK/source ZIP.

For a tagged build such as `v2.1.0`, the workflow:

1. restores the keystore to a temporary file;
2. validates the alias/password;
3. builds `assembleRelease`;
4. verifies the resulting APK with `apksigner --print-certs`;
5. publishes the signed APK directly as a GitHub Release asset;
6. removes the temporary keystore.

For local builds, set these environment variables before running `./gradlew assembleRelease`:

```bash
export SIGNING_KEYSTORE_PATH=/absolute/path/to/localis-release.jks
export SIGNING_STORE_PASSWORD=YOUR_KEYSTORE_PASSWORD
export SIGNING_KEY_ALIAS=YOUR_KEY_ALIAS
export SIGNING_KEY_PASSWORD=YOUR_KEY_PASSWORD
```
