#!/usr/bin/env bash
# 월드에딧(Paper용)을 Modrinth 에서 받아 plugins 폴더에 넣습니다. start.sh 가 처음에 한 번 부릅니다.
# 직접 실행: ./get-worldedit.sh [마인크래프트버전]   (기본 26.2)
cd "$(dirname "$0")"
mkdir -p plugins
GV="${1:-26.2}"
UA="JunseoCity-server-kit (github.com/obst2580/Junseo)"
api() {
  local url="https://api.modrinth.com/v2/project/worldedit/version?loaders=%5B%22paper%22%2C%22bukkit%22%5D"
  [ -n "$1" ] && url="$url&game_versions=%5B%22$1%22%5D"
  curl -fsSL -A "$UA" "$url"
}
pick() {
  python3 -c '
import json, sys
v = json.load(sys.stdin)
if not v: sys.exit(1)
f = next((f for f in v[0]["files"] if f.get("primary")), v[0]["files"][0])
print(v[0]["version_number"]); print(f["filename"]); print(f["url"])'
}
info=$(api "$GV" | pick) || { echo "[월드에딧] $GV 용이 아직 없어서 최신 판을 받아요."; info=$(api "" | pick); } || {
  echo "[월드에딧] 받지 못했어요. https://modrinth.com/plugin/worldedit 에서 받아 plugins 폴더에 넣어 주세요."
  exit 1
}
ver=$(echo "$info" | sed -n 1p); name=$(echo "$info" | sed -n 2p); url=$(echo "$info" | sed -n 3p)
echo "[월드에딧] $ver ($name) 받는 중..."
curl -fsSL -A "$UA" -o "plugins/$name" "$url" && echo "[월드에딧] plugins/$name 에 저장했어요."
