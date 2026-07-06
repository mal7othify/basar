#!/usr/bin/env bash
# Build, install, and launch the demo app on a connected iPhone — no Xcode UI.
#
# Usage: scripts/run-ios.sh [device-udid]
# Without an argument, the first connected device is used.
#
# Requirements (one-time, already true if you've ever run from Xcode):
#   - device connected via USB (or same-network wireless debugging)
#   - Developer Mode enabled on the phone
#   - signing team configured in the Xcode project
set -euo pipefail
cd "$(dirname "$0")/.."

DEVICE="${1:-$(xcrun devicectl list devices 2>/dev/null |
    awk '/connected/ { print $(NF-1); exit }')}"
if [[ -z "$DEVICE" ]]; then
    echo "No connected iOS device found (xcrun devicectl list devices)" >&2
    exit 1
fi
echo "==> Building for device $DEVICE"

DERIVED=iosApp/build/device
xcodebuild \
    -project iosApp/iosApp.xcodeproj \
    -scheme iosApp \
    -configuration Debug \
    -destination "platform=iOS,id=$DEVICE" \
    -derivedDataPath "$DERIVED" \
    -allowProvisioningUpdates \
    build

APP=$(find "$DERIVED/Build/Products/Debug-iphoneos" -maxdepth 1 -name '*.app' | head -1)
if [[ -z "$APP" ]]; then
    echo "No .app found in $DERIVED/Build/Products/Debug-iphoneos" >&2
    exit 1
fi
BUNDLE_ID=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$APP/Info.plist")

echo "==> Installing $BUNDLE_ID"
xcrun devicectl device install app --device "$DEVICE" "$APP"

echo "==> Launching"
xcrun devicectl device process launch --device "$DEVICE" "$BUNDLE_ID"

echo "==> Done — the app should be on screen (unlock the phone if needed)"
