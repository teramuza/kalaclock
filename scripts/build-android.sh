#!/usr/bin/env bash
# Builds the Android release (APK and AAB) into dist/android/.
#
# Usage: scripts/build-android.sh [--debug-sign] [--skip-tests]
#   --debug-sign  sign the APK with the local debug key when no keystore.properties exists,
#                 so it can be installed on a device for testing (not for distribution)
#   --skip-tests  skip the unit tests
#
# For a distributable build, create android/keystore.properties (see keystore.properties.example).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID="$ROOT/android"
OUT="$ROOT/dist/android"
DEBUG_SIGN=0
RUN_TESTS=1
for arg in "$@"; do
  case "$arg" in
    --debug-sign) DEBUG_SIGN=1 ;;
    --skip-tests) RUN_TESTS=0 ;;
    *) echo "Unknown option: $arg" >&2; exit 1 ;;
  esac
done

VERSION="$(grep -E 'versionName' "$ANDROID/app/build.gradle.kts" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
echo "==> KalaClock Android $VERSION"

cd "$ANDROID"
TASKS=(:app:assembleRelease :app:bundleRelease)
if [ "$RUN_TESTS" = 1 ]; then TASKS=(:app:testDebugUnitTest "${TASKS[@]}"); fi
./gradlew "${TASKS[@]}"

mkdir -p "$OUT"
APK_SRC="$ANDROID/app/build/outputs/apk/release"
AAB_SRC="$ANDROID/app/build/outputs/bundle/release/app-release.aab"

if [ -f "$ANDROID/keystore.properties" ]; then
  cp "$APK_SRC/app-release.apk" "$OUT/KalaClock-$VERSION.apk"
  echo "==> Signed with keystore.properties"
elif [ "$DEBUG_SIGN" = 1 ]; then
  BT="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* | sort -V | tail -1)"
  "$BT/zipalign" -f 4 "$APK_SRC/app-release-unsigned.apk" "$OUT/.aligned.apk"
  "$BT/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/KalaClock-$VERSION.apk" "$OUT/.aligned.apk"
  rm -f "$OUT/.aligned.apk" "$OUT/KalaClock-$VERSION.apk.idsig"
  echo "==> Signed with the DEBUG key (testing only, not for distribution)"
else
  cp "$APK_SRC/app-release-unsigned.apk" "$OUT/KalaClock-$VERSION-unsigned.apk"
  echo "==> No keystore.properties: APK is unsigned. Use --debug-sign for a test install."
fi
cp "$AAB_SRC" "$OUT/KalaClock-$VERSION.aab"

echo "==> Output:"
ls -lh "$OUT"
