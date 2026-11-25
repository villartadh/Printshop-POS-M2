@echo off
setlocal
cd /d "%~dp0"
where node >nul 2>&1 || (echo Node.js 22 LTS is required.& pause & exit /b 1)
node scripts\ldc.mjs
if errorlevel 1 pause
