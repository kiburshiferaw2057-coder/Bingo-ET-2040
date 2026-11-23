#!/usr/bin/env bash
set -e
if command -v gradle >/dev/null 2>&1; then
  gradle assembleDebug
elif [ -x "./gradlew" ]; then
  ./gradlew assembleDebug
else
  echo "Gradle not found in PATH. Open this folder in Android Studio or run via GitHub Actions."
  exit 1
fi
echo "APK generated at: app/build/outputs/apk/debug/app-debug.apk"
