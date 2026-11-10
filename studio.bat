@echo off
chcp 65001 >nul
rem studio 화면 실행(윈도우). 더블클릭하면 브라우저에 화면이 뜹니다.
rem 처음 한 번은 uv 파이썬 도구와 패키지 설치로 몇 분 걸립니다. 이 창을 닫으면 화면도 꺼집니다.
cd /d "%~dp0"
set "PATH=%USERPROFILE%\.local\bin;%PATH%"

where uv >nul 2>nul
if errorlevel 1 (
  echo uv 파이썬 도구를 설치합니다...
  powershell -NoProfile -ExecutionPolicy ByPass -Command "irm https://astral.sh/uv/install.ps1 | iex"
)
where uv >nul 2>nul
if errorlevel 1 (
  echo uv 설치 실패 - https://docs.astral.sh/uv/ 참고
  pause
  exit /b 1
)
where ffmpeg >nul 2>nul
if errorlevel 1 echo [주의] ffmpeg가 없습니다. 영상 자료 구간 자르기에 필요: winget install Gyan.FFmpeg

echo 필요한 패키지를 확인합니다...
uv sync --quiet
if errorlevel 1 (
  echo 패키지 설치 실패
  pause
  exit /b 1
)
if not exist .env copy .env.example .env >nul
uv run studio %*
pause
