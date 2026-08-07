#!/usr/bin/env bash
# Compiles the .mat sources in spatial-render-filament/materials/ into
# .filamat packages (all backends, mobile) using the version-pinned matc.
# Compiled output is CHECKED IN (materials/compiled/) so ordinary builds
# never need the Filament host tools; re-run this after editing a .mat or
# bumping the Filament version.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MATC="$ROOT/native/filament-bridge/third_party/tools/matc"
SRC="$ROOT/spatial-render-filament/materials"
OUT="$SRC/compiled"

"$ROOT/scripts/fetch-filament-tools.sh"

ANDROID_ASSETS="$ROOT/spatial-render-filament/src/androidMain/assets/materials"

mkdir -p "$OUT" "$ANDROID_ASSETS"
shopt -s nullglob
for mat in "$SRC"/*.mat; do
    name="$(basename "$mat" .mat)"
    echo "matc: $name.mat → compiled/$name.filamat"
    "$MATC" --api all --platform mobile -o "$OUT/$name.filamat" "$mat"
    # Android loads materials as AAR assets; keep that copy in sync.
    cp "$OUT/$name.filamat" "$ANDROID_ASSETS/$name.filamat"
done
echo "Done."
