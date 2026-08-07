#!/usr/bin/env bash
# Builds native/filament-bridge/dist/FilamentBridge.xcframework:
# the FilamentBridge ObjC++ wrapper compiled per slice and merged (libtool)
# with the required prebuilt Filament static libraries into ONE archive per
# slice, so downstream (the iosApp Xcode project) links a single library.
#
# Prereq: scripts/fetch-filament-ios.sh (run automatically if needed).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BRIDGE="$ROOT/native/filament-bridge"
FILAMENT="$BRIDGE/third_party/filament"
DIST="$BRIDGE/dist"
BUILD="$BRIDGE/build"

# Matches iosApp's IPHONEOS_DEPLOYMENT_TARGET.
MIN_IOS="16.6"

# Filament archives merged into the bridge. Lean-but-safe set for gltfio
# rendering; extend if the app link reports missing filament symbols.
FILAMENT_LIBS=(
    filament backend filabridge filaflat utils abseil
    geometry shaders smol-v ibl filament-iblprefilter
    gltfio_core uberarchive uberzlib ktxreader basis_transcoder
    dracodec meshoptimizer mikktspace stb zstd image camutils
)

"$ROOT/scripts/fetch-filament-ios.sh"

rm -rf "$BUILD" "$DIST"
mkdir -p "$BUILD" "$DIST"

CXXFLAGS=(
    -std=c++17 -O2 -g
    -fobjc-arc -fno-exceptions -fno-rtti
    -fmodules -fcxx-modules
    -I"$BRIDGE/include"
    -I"$FILAMENT/include"
    -miphoneos-version-min="$MIN_IOS"
)

compile() { # <sdk> <target-triple> <out.o>
    local sdk="$1" triple="$2" out="$3"
    xcrun --sdk "$sdk" clang++ "${CXXFLAGS[@]}" -target "$triple" \
        -c "$BRIDGE/src/FilamentBridge.mm" -o "$out"
}

merge() { # <slice-dir-name> <bridge.o> <out.a>
    local slice="$1" obj="$2" out="$3"
    local libs=("$obj")
    local name
    for name in "${FILAMENT_LIBS[@]}"; do
        local archive="$FILAMENT/lib/lib${name}.xcframework/$slice/lib${name}.a"
        if [[ ! -f "$archive" ]]; then
            echo "error: missing $archive" >&2
            exit 1
        fi
        libs+=("$archive")
    done
    xcrun libtool -static -no_warning_for_no_symbols -o "$out" "${libs[@]}"
}

echo "— device (arm64)"
compile iphoneos "arm64-apple-ios${MIN_IOS}" "$BUILD/bridge-device.o"
merge ios-arm64 "$BUILD/bridge-device.o" "$BUILD/libFilamentBridge-device.a"

echo "— simulator (arm64 + x86_64)"
compile iphonesimulator "arm64-apple-ios${MIN_IOS}-simulator" "$BUILD/bridge-sim-arm64.o"
compile iphonesimulator "x86_64-apple-ios${MIN_IOS}-simulator" "$BUILD/bridge-sim-x86_64.o"
lipo -create "$BUILD/bridge-sim-arm64.o" "$BUILD/bridge-sim-x86_64.o" -output "$BUILD/bridge-sim.o"
merge ios-arm64_x86_64-simulator "$BUILD/bridge-sim.o" "$BUILD/libFilamentBridge-sim.a"

echo "— xcframework"
xcodebuild -create-xcframework \
    -library "$BUILD/libFilamentBridge-device.a" -headers "$BRIDGE/include" \
    -library "$BUILD/libFilamentBridge-sim.a" -headers "$BRIDGE/include" \
    -output "$DIST/FilamentBridge.xcframework"

echo "Built $DIST/FilamentBridge.xcframework"
