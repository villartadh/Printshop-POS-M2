@echo off
setlocal
cd /d "%~dp0..\.."
where node >nul 2>&1 || (echo Node.js 22 LTS is required.& pause & exit /b 1)
node scripts\windows\start-native-acceptance.mjs
set "RESULT=%errorlevel%"
pause
exit /b %RESULT%
