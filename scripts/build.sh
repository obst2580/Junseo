#!/usr/bin/env bash
# 맵 데이터 → Studio에서 여는 게임 파일 (dist/SeoulRP.rbxl)
#
#   scripts/build.sh            map/data/*.json으로 게임 파일만 다시 만든다
#   scripts/build.sh --data     지도 원본을 받아 map/data/*.json부터 새로 만든다 (파이썬 필요)
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${1:-}" == "--data" ]]; then
  python3 tools/mapdata/fetch.py
  (cd tools/mapdata && python3 build_data.py)
fi

lune run map/generate
mkdir -p dist
rojo build default.project.json -o dist/SeoulRP.rbxl
echo "완료: dist/SeoulRP.rbxl"
