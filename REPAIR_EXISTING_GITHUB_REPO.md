# Existing GitHub repository repair

This package is safe to upload over the existing repository.

## What was fixed

- `build-apk.yml` is retained as a **disabled migration shim** so an existing repository overwrites the old auto-building workflow instead of leaving the stale workflow active.
- Only `android-release.yml` has automatic push/tag/PR triggers.
- No signing keystore is included in the source tree.
- Release signing is restored only from GitHub Actions Secrets.
- The release audit accepts the disabled migration shim and still rejects any other duplicate workflow.

## After uploading

Configure these four GitHub Actions Secrets for signed `v*` releases:

- `SIGNING_KEYSTORE_B64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS` = `localis`
- `SIGNING_KEY_PASSWORD`

Do not upload `localis-release.jks` to the repository.

If the old `build-apk.yml` is already present in the GitHub repository, uploading this package over the repository will overwrite it with the disabled shim.
