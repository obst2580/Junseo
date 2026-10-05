#!/usr/bin/env bash
# 준서 시티 서버 실행 (macOS / Linux)
# 1) 이 폴더에 paper.jar 넣기  2) plugins 폴더에 JunseoCity-*.jar 넣기  3) ./start.sh
set -e
cd "$(dirname "$0")"

if [ ! -f paper.jar ]; then
  echo "[!] paper.jar 가 없어요."
  echo "    https://papermc.io/downloads/paper 에서 받아서 이 폴더에 paper.jar 이름으로 저장하세요."
  exit 1
fi

mkdir -p plugins

# 월드에딧(건축 도구): 없으면 처음 한 번 Modrinth 에서 받음
if ! ls plugins/worldedit*.jar >/dev/null 2>&1 && ! ls plugins/WorldEdit*.jar >/dev/null 2>&1; then
  echo "월드에딧을 처음 한 번 받는 중..."
  ./get-worldedit.sh || true
fi

if [ ! -f eula.txt ]; then
  echo "서버를 실행하려면 마인크래프트 EULA에 동의해야 해요: https://aka.ms/MinecraftEULA"
  read -r -p "동의하나요? (Y 입력 후 엔터): " agree
  if [ "$agree" = "Y" ] || [ "$agree" = "y" ]; then
    echo "eula=true" > eula.txt
  else
    echo "동의하지 않아서 서버를 시작하지 않아요."
    exit 1
  fi
fi

# 처음 켤 때: 보이는 거리 16청크 (도시가 멀리 보임), 계산 거리 8 (CPU 가볍게)
if [ ! -f server.properties ]; then
    printf 'view-distance=16\nsimulation-distance=8\n' > server.properties
fi

exec java -Xms2G -Xmx4G -jar paper.jar --nogui
