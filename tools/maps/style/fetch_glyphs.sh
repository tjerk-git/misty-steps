#!/bin/sh
# Fetch the Noto Sans glyph ranges the map style needs: Latin and Latin extended (0-767), regular and bold.
# Source: https://github.com/openmaptiles/fonts (SIL Open Font License). Run once; the result is committed.
set -eu
dest="$(dirname "$0")/../../../app/src/main/assets/maps/glyphs"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
gh release download v2.0 --repo openmaptiles/fonts --pattern noto-sans.zip --dir "$tmp"
unzip -q "$tmp/noto-sans.zip" -d "$tmp/fonts"
for font in "Noto Sans Regular" "Noto Sans Bold"; do
  mkdir -p "$dest/$font"
  for range in 0-255 256-511 512-767; do
    cp "$tmp/fonts/$font/$range.pbf" "$dest/$font/$range.pbf"
  done
done
cp "$tmp/fonts/OFL.txt" "$dest/LICENSE.txt" 2>/dev/null || echo "Noto Sans, SIL Open Font License 1.1, via github.com/openmaptiles/fonts" > "$dest/LICENSE.txt"
