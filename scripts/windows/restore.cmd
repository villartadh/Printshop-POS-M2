@echo off
setlocal
cd /d "%~dp0"
runtime\node.exe scripts\windows\lifecycle.mjs restore "%~1"
set "result=%errorlevel%"
pause
exit /b %result%
