# Signing Guide for Localis

Localis uses Android release signing to produce signed APKs. This document explains how to configure signing safely without committing keys to Git.

## 1. Generate a new signing keystore (if you do not have one)

```bash
keytool -genkey -v -keystore localis-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias localis
```

You will be asked for:
- Keystore password
- Key alias password
- Your name, organization, city, state, country

Keep this `.jks` file safe. **Never commit it to Git.**

## 2. Configure local signing (for manual builds)

Create a `local.properties` file in the project root:

```properties
signing.keystore.file=/absolute/path/to/localis-release.jks
signing.keystore.password=YOUR_KEYSTORE_PASSWORD
signing.key.alias=localis
signing.key.password=YOUR_KEY_PASSWORD
```

`local.properties` is already in `.gitignore` and will not be tracked.

Alternatively, set environment variables before building:

```bash
export SIGNING_KEYSTORE_PATH=/absolute/path/to/localis-release.jks
export SIGNING_STORE_PASSWORD=YOUR_KEYSTORE_PASSWORD
export SIGNING_KEY_ALIAS=localis
export SIGNING_KEY_PASSWORD=YOUR_KEY_PASSWORD
./gradlew assembleRelease
```

## 3. Configure GitHub Actions signing (for CI builds)

Go to your GitHub repository **Settings > Secrets and variables > Actions**, then add:

| Secret name | Value |
|---|---|
| `SIGNING_KEYSTORE_B64` | Base64-encoded content of your `.jks` file |
| `SIGNING_STORE_PASSWORD` | Your keystore password |
| `SIGNING_KEY_ALIAS` | Your key alias (e.g., `localis`) |
| `SIGNING_KEY_PASSWORD` | Your key password |

### How to create the Base64 secret

```bash
base64 -w 0 localis-release.jks
```

Copy the output and paste it into the `SIGNING_KEYSTORE_B64` secret.

### How the CI workflow works

The GitHub Actions workflow will:
1. Decode the Base64 keystore into a temporary file (`/tmp/signing.keystore`)
2. Set the `SIGNING_KEYSTORE_PATH` environment variable
3. Build the signed APK
4. Delete the temporary keystore immediately after build

**The keystore is never written to the repository or release artifacts.**

## 4. If your previous signing key was exposed

If `localis-release.jks` (or any keystore) was ever committed to a public GitHub repository, it is compromised:

1. **Generate a new keystore** (see step 1)
2. **Upload the new keystore Base64** to GitHub Secrets
3. **Revoke the old keystore** if it was used for Google Play — contact Google Play Console support to request a key reset
4. **Never reuse the old keystore**

## 5. Security checklist

- [ ] `.jks` and `.keystore` files are in `.gitignore`
- [ ] `local.properties` is in `.gitignore`
- [ ] No hardcoded passwords in `build.gradle.kts` or workflow files
- [ ] GitHub Actions reads signing secrets from `secrets.*` only
- [ ] Keystore file is stored in a secure location with limited access
- [ ] Keystore passwords are strong and unique
- [ ] If the keystore was exposed, a new one has been generated
