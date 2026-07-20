#!/usr/bin/env bash
# Compiles app/src/main/materials/*.mat into app/src/main/assets/materials/*.filamat.
#
# The matc binary MUST match the filament-android runtime version in
# gradle/libs.versions.toml (material format is version-locked). Download the
# matching release archive from https://github.com/google/filament/releases
# (e.g. filament-v1.73.0-mac.tgz) and point MATC at its bin/matc, or place
# matc on your PATH.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/materials"
OUT="$ROOT/app/src/main/assets/materials"
MATC="${MATC:-matc}"

if ! command -v "$MATC" >/dev/null 2>&1; then
    echo "error: matc not found. Set MATC=/path/to/matc (Filament release bin/matc)." >&2
    exit 1
fi

mkdir -p "$OUT"
for mat in "$SRC"/*.mat; do
    name="$(basename "$mat" .mat)"
    echo "matc: $name.mat -> $name.filamat"
    "$MATC" -a opengl -a vulkan -p mobile -o "$OUT/$name.filamat" "$mat"
done
echo "done."
