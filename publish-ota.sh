#!/usr/bin/env bash
set -e

# Script: publish-ota.sh
# Purpose: Build Android APK, archive by version number on Mac, and publish the single latest APK to the Pi OTA server.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILDS_DIR="$SCRIPT_DIR/builds"

mkdir -p "$BUILDS_DIR"

RELEASE_NOTES="${1:-"Routine maintenance & performance updates"}"

# Read current versionCode and versionName from build.gradle.kts
GRADLE_FILE="$SCRIPT_DIR/app/build.gradle.kts"
VERSION_CODE=$(grep "versionCode" "$GRADLE_FILE" | head -n 1 | awk -F '=' '{print $2}' | tr -d ' ')
VERSION_NAME=$(grep "versionName" "$GRADLE_FILE" | head -n 1 | sed -E 's/.*"([^"]+)".*/\1/')

echo "==========================================="
echo "Building Argon Mobile Hub v${VERSION_NAME} (Build ${VERSION_CODE})"
echo "Notes: ${RELEASE_NOTES}"
echo "==========================================="

# Ensure environment
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/jdk-17/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

cd "$SCRIPT_DIR"
./gradlew assembleDebug

COMPILED_APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$COMPILED_APK" ]; then
    echo "Error: Compiled APK not found at $COMPILED_APK"
    exit 1
fi

# 1. Archive on Mac by version number
ARCHIVED_APK="$BUILDS_DIR/argon-hub-v${VERSION_NAME}-b${VERSION_CODE}.apk"
cp "$COMPILED_APK" "$ARCHIVED_APK"
echo "✅ Archived on Mac: $ARCHIVED_APK"

# 2. Update version.json
VERSION_JSON="$BUILDS_DIR/version.json"
NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
cat <<EOF > "$VERSION_JSON"
{
  "versionCode": ${VERSION_CODE},
  "versionName": "${VERSION_NAME}",
  "releaseDate": "${NOW}",
  "apkPath": "/argon-hub.apk",
  "releaseNotes": "${RELEASE_NOTES}"
}
EOF
echo "✅ Generated version metadata: $VERSION_JSON"

# If local sibling my-pi/argon-ota/dist exists, keep it synced as well
LOCAL_OTA_DIR="$ROOT_DIR/my-pi/argon-ota/dist"
if [ -d "$LOCAL_OTA_DIR" ]; then
    cp "$COMPILED_APK" "$LOCAL_OTA_DIR/argon-hub.apk"
    cp "$VERSION_JSON" "$LOCAL_OTA_DIR/version.json"
fi

# 3. Synchronize to Raspberry Pi (overwriting to ensure ONLY 1 APK exists on Pi)
echo "🚀 Publishing single latest APK to Raspberry Pi OTA server..."
scp "$VERSION_JSON" pi:~/my-pi/argon-ota/dist/version.json
scp "$COMPILED_APK" pi:~/my-pi/argon-ota/dist/argon-hub.apk

echo "==========================================="
echo "🎉 Successfully published OTA Update v${VERSION_NAME} to Pi!"
echo "Mobile devices will auto-detect update upon network refresh."
echo "Web URL: http://argon-host:5050/"
echo "==========================================="
