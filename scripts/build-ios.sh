#!/usr/bin/env bash
# Archives the iOS release into dist/ios/ (and exports an .ipa when a team is given).
#
# Usage: DEVELOPMENT_TEAM=ABCDE12345 [EXPORT_METHOD=app-store-connect] scripts/build-ios.sh [--skip-tests]
#   DEVELOPMENT_TEAM  your Apple team ID. Without it the archive is built UNSIGNED, which only
#                     proves the release build compiles; it cannot be installed or uploaded.
#   EXPORT_METHOD     app-store-connect (default), release-testing, debugging or enterprise
#   --skip-tests      skip the unit tests
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IOS="$ROOT/ios"
OUT="$ROOT/dist/ios"
RUN_TESTS=1
for arg in "$@"; do
  case "$arg" in
    --skip-tests) RUN_TESTS=0 ;;
    *) echo "Unknown option: $arg" >&2; exit 1 ;;
  esac
done

VERSION="$(grep -E 'MARKETING_VERSION' "$IOS/project.yml" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
BUILD="$(grep -E 'CURRENT_PROJECT_VERSION' "$IOS/project.yml" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
echo "==> KalaClock iOS $VERSION ($BUILD)"

cd "$IOS"
if command -v xcodegen >/dev/null 2>&1; then
  xcodegen generate
else
  echo "xcodegen not found, using the checked-in KalaClock.xcodeproj"
fi

if [ "$RUN_TESTS" = 1 ]; then
  SIM_ID="$(xcrun simctl list devices available | grep -m1 -oE 'iPhone[^(]*\(([0-9A-F-]{36})\)' | grep -oE '[0-9A-F-]{36}')"
  xcodebuild test -project KalaClock.xcodeproj -scheme KalaClock \
    -destination "platform=iOS Simulator,id=$SIM_ID" -quiet
fi

mkdir -p "$OUT"
ARCHIVE="$OUT/KalaClock.xcarchive"
rm -rf "$ARCHIVE"

if [ -n "${DEVELOPMENT_TEAM:-}" ]; then
  xcodebuild archive -project KalaClock.xcodeproj -scheme KalaClock -configuration Release \
    -destination 'generic/platform=iOS' -archivePath "$ARCHIVE" \
    DEVELOPMENT_TEAM="$DEVELOPMENT_TEAM" CODE_SIGN_STYLE=Automatic -allowProvisioningUpdates -quiet

  cat > "$OUT/ExportOptions.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>method</key><string>${EXPORT_METHOD:-app-store-connect}</string>
  <key>teamID</key><string>$DEVELOPMENT_TEAM</string>
  <key>signingStyle</key><string>automatic</string>
</dict></plist>
PLIST
  xcodebuild -exportArchive -archivePath "$ARCHIVE" -exportOptionsPlist "$OUT/ExportOptions.plist" \
    -exportPath "$OUT" -allowProvisioningUpdates -quiet
  echo "==> Signed build exported"
else
  xcodebuild archive -project KalaClock.xcodeproj -scheme KalaClock -configuration Release \
    -destination 'generic/platform=iOS' -archivePath "$ARCHIVE" \
    CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO -quiet
  echo "==> No DEVELOPMENT_TEAM: archive is UNSIGNED (compile check only)."
fi

echo "==> Output:"
ls -lh "$OUT"
