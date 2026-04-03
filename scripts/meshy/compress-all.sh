#!/bin/bash
set -e
SRC_FINAL="scripts/meshy/output-final"
SRC_UNITS="scripts/meshy/output"
SRC_TERRAIN="scripts/meshy/output-terrain"
OUT="scripts/meshy/output-cdn"
mkdir -p "$OUT/castles" "$OUT/units" "$OUT/terrain"

compress() {
  local src=$1 dst=$2
  echo "  $src → $dst"
  npx @gltf-transform/cli resize "$src" "$dst" --width 512 --height 512 2>/dev/null
  npx @gltf-transform/cli draco "$dst" "$dst" 2>/dev/null
}

echo "=== 성곽 8종 ==="
for f in "$SRC_FINAL"/*.glb; do
  compress "$f" "$OUT/castles/$(basename $f)"
done

echo "=== 유닛 37종 ==="
for f in "$SRC_UNITS"/unit_*.glb; do
  compress "$f" "$OUT/units/$(basename $f)"
done

echo "=== 지형 5종 ==="
for f in "$SRC_TERRAIN"/*.glb; do
  compress "$f" "$OUT/terrain/$(basename $f)"
done

echo "=== 결과 ==="
du -sh "$OUT/castles" "$OUT/units" "$OUT/terrain" "$OUT"
echo "=== 개별 크기 ==="
ls -lhS "$OUT/castles/" "$OUT/units/" "$OUT/terrain/"
