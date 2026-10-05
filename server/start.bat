@echo off
rem JunseoCity server launcher (Windows)
rem 1) Put paper.jar in this folder  2) Put JunseoCity-*.jar in the plugins folder  3) Double-click this file
cd /d "%~dp0"
title JunseoCity Server

if not exist paper.jar (
  echo [!] paper.jar not found.
  echo     Download Paper from https://papermc.io/downloads/paper
  echo     and save it in this folder as paper.jar
  pause
  exit /b 1
)

if not exist plugins mkdir plugins

rem WorldEdit (building tool): download once if missing
if not exist "plugins\worldedit*.jar" (
  echo Downloading WorldEdit for the first time...
  powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0get-worldedit.ps1"
)

if not exist eula.txt (
  echo You must agree to the Minecraft EULA to run a server:
  echo   https://aka.ms/MinecraftEULA
  set /p AGREE=Do you agree? Type Y and press Enter:
  call :checkEula
)

java -Xms2G -Xmx4G -jar paper.jar --nogui
pause
exit /b 0

:checkEula
if /i "%AGREE%"=="Y" (
  echo eula=true> eula.txt
) else (
  echo You did not agree to the EULA. The server will not start.
  pause
  exit 1
)
exit /b 0
