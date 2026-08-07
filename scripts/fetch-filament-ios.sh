#!/usr/bin/env bash
# Downloads the prebuilt Filament static libraries for iOS (device + simulator
# XCFramework slices, including gltfio) into native/filament-bridge/third_party.
# Pinned to the version in gradle/libs.versions.toml — the iOS libs, the
# Android Maven artifacts, and the matc host tools must stay in lockstep
# (.filamat files are matc-version-locked).
set -euo pipefail

FILAMENT_VERSION="1.74.0"
SHA256="880ed17b49b53b2b144e5a4ed5fc6a5766e4c0a613f58dd9d114408bd08f7fc4"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
THIRD_PARTY="$ROOT/native/filament-bridge/third_party"
TGZ="$THIRD_PARTY/filament-v${FILAMENT_VERSION}-ios.tgz"
URL="https://github.com/google/filament/releases/download/v${FILAMENT_VERSION}/filament-v${FILAMENT_VERSION}-ios.tgz"

if [[ -f "$THIRD_PARTY/filament/.version" ]] \
    && [[ "$(cat "$THIRD_PARTY/filament/.version")" == "$FILAMENT_VERSION" ]]; then
    echo "Filament iOS $FILAMENT_VERSION already present — nothing to do."
    exit 0
fi

mkdir -p "$THIRD_PARTY"
if [[ ! -f "$TGZ" ]]; then
    echo "Downloading $URL"
    curl -fL --retry 3 -o "$TGZ" "$URL"
fi

echo "$SHA256  $TGZ" | shasum -a 256 -c - >/dev/null

rm -rf "$THIRD_PARTY/filament"
tar -xzf "$TGZ" -C "$THIRD_PARTY"
echo "$FILAMENT_VERSION" > "$THIRD_PARTY/filament/.version"
echo "Filament iOS $FILAMENT_VERSION ready in $THIRD_PARTY/filament"
