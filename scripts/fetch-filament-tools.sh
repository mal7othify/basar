#!/usr/bin/env bash
# Downloads the Filament host tools (matc, cmgen, ...) for macOS, pinned to
# the same version as the runtime (gradle/libs.versions.toml) — .filamat
# files are matc-version-locked. Extracts just matc into
# native/filament-bridge/third_party/tools/.
set -euo pipefail

FILAMENT_VERSION="1.74.0"
SHA256="f606f776ad0ac53eb5ed6f266cd7c3f37f18e160bc5cfa7ea291ffd12182f092"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$ROOT/native/filament-bridge/third_party/tools"
TGZ="$TOOLS/filament-v${FILAMENT_VERSION}-mac.tgz"
URL="https://github.com/google/filament/releases/download/v${FILAMENT_VERSION}/filament-v${FILAMENT_VERSION}-mac.tgz"

# matc --version prints the minor version only ("74" for 1.74.x)
if [[ -x "$TOOLS/matc" ]] && [[ "$("$TOOLS/matc" --version 2>/dev/null)" == "$(cut -d. -f2 <<< "$FILAMENT_VERSION")" ]]; then
    echo "matc $("$TOOLS/matc" --version) already present — nothing to do."
    exit 0
fi

mkdir -p "$TOOLS"
echo "Downloading $URL"
curl -fL --retry 3 -o "$TGZ" "$URL"
echo "$SHA256  $TGZ" | shasum -a 256 -c - >/dev/null

tar -xzf "$TGZ" -C "$TOOLS" --strip-components=2 filament/bin/matc
chmod +x "$TOOLS/matc"
rm -f "$TGZ"
echo "matc $("$TOOLS/matc" --version) ready in $TOOLS"
