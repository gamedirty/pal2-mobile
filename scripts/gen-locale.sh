#!/bin/sh
# Generate the zh_TW.UTF-8 glibc locale files that ship in app/src/main/assets/pal2-locale.
# Run inside WSL/Ubuntu; copy the output directory contents into app/src/main/assets/pal2-locale/.
set -e
OUT=${1:-/tmp/loc}
mkdir -p "$OUT"
localedef -i zh_TW -f UTF-8 "$OUT/zh_TW.UTF-8"
echo "generated: $OUT/zh_TW.UTF-8"
