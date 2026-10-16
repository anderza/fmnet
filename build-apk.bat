@echo off
chcp 65001 >nul
title RadioNet - сборка APK
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-apk.ps1"
echo.
pause
