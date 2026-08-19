#!/bin/bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

echo "=== Localis APK Builder ==="
echo ""

# Check Java (need JDK, not just JRE)
if ! command -v javac &>/dev/null; then
    echo "ERROR: javac not found. Install JDK (not just JRE):"
    echo "  Ubuntu: sudo apt install openjdk-17-jdk"
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | grep -oP 'version "\K[^"]+')
echo "Java: $JAVA_VER"

# Check Android SDK
if [ -z "$ANDROID_HOME" ]; then
    # Try common paths
    for p in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/usr/lib/android-sdk"; do
        if [ -d "$p" ]; then
            export ANDROID_HOME="$p"
            echo "Found Android SDK: $ANDROID_HOME"
            break
        fi
    done
fi

if [ -z "$ANDROID_HOME" ] || [ ! -d "$ANDROID_HOME" ]; then
    echo "ERROR: Android SDK not found. Set ANDROID_HOME or install Android Studio."
    echo "  1. Download from https://developer.android.com/studio"
    echo "  2. Or command line tools:"
    echo "     export ANDROID_HOME=~/Android/Sdk"
    echo "     sdkmanager 'platforms;android-35' 'build-tools;35.0.0'"
    exit 1
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Check Gradle
if [ ! -f "./gradlew" ]; then
    echo "ERROR: gradlew not found. This project needs the Gradle wrapper."
    echo "  Run in Android Studio (File -> Open -> Select this folder -> Build)"
    echo "  Or: gradle wrapper --gradle-version 8.9"
    exit 1
fi
chmod +x ./gradlew

# Release signing is intentionally external. Never generate a keystore with a
# hard-coded password in the repository. For a local signed build, export:
# SIGNING_KEYSTORE_PATH, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD.
if [ -n "${SIGNING_KEYSTORE_PATH:-}" ]; then
    export SIGNING_KEYSTORE_PATH SIGNING_STORE_PASSWORD SIGNING_KEY_ALIAS SIGNING_KEY_PASSWORD
fi

# Build
echo ""
echo "Building Release APK..."
./gradlew assembleRelease --no-daemon

APK="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
    APK="$PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
fi
if [ -f "$APK" ]; then
    echo ""
    echo "SUCCESS!"
    echo "  APK: $APK"
    echo "  Size: $(du -h "$APK" | cut -f1)"
    echo ""
    echo "Install: adb install '$APK'"
else
    echo "Build may have failed. Check output above."
    exit 1
fi
