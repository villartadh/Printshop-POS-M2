@echo off
setlocal
cd /d "%~dp0"
where node >nul 2>&1 || (echo Install Node.js 22 LTS first. & pause & exit /b 1)
node scripts\install.mjs
if errorlevel 1 (pause & exit /b 1)
explorer "%LOCALAPPDATA%\Printshop POS\releases\0.9.2"
pause
