#!/bin/bash
# studio 화면 실행(맥·리눅스). Finder에서 더블클릭하면 브라우저에 화면이 뜹니다.
# 처음 한 번은 uv(파이썬 도구)와 패키지 설치로 몇 분 걸립니다. 이 창을 닫으면 화면도 꺼집니다.
cd "$(dirname "$0")" || exit 1
export PATH="$HOME/.local/bin:$HOME/.cargo/bin:/opt/homebrew/bin:/usr/local/bin:$PATH"

pause_exit() { read -r -p "엔터를 누르면 창이 닫힙니다 " _; exit 1; }

if ! command -v uv >/dev/null 2>&1; then
  echo "uv(파이썬 도구)를 설치합니다…"
  curl -LsSf https://astral.sh/uv/install.sh | sh || { echo "uv 설치 실패 — https://docs.astral.sh/uv/ 참고"; pause_exit; }
  export PATH="$HOME/.local/bin:$PATH"
fi
if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "⚠️  ffmpeg가 없습니다(영상 자료 구간 자르기에 필요). 설치: brew install ffmpeg   (Homebrew: https://brew.sh)"
fi
echo "필요한 패키지를 확인합니다…"
uv sync --quiet || { echo "패키지 설치 실패"; pause_exit; }
[ -f .env ] || cp .env.example .env
uv run studio "$@" || pause_exit
