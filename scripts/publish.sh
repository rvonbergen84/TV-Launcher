#!/usr/bin/env bash
# Builds the release APK and copies it plus version.txt into dist/,
# which is where the in-app updater looks for new versions.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew assembleRelease -q
cp app/build/outputs/apk/release/app-release.apk dist/tv-launcher.apk
code=$(grep -E 'versionCode = ' app/build.gradle.kts | grep -oE '[0-9]+')
name=$(grep -E 'versionName = ' app/build.gradle.kts | grep -oE '"[^"]+"' | tr -d '"')
printf '%s\n%s\n' "$code" "$name" > dist/version.txt
echo "Published $name ($code) to dist/"
