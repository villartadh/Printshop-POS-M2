@echo off
setlocal
cd /d "%~dp0"
runtime\node.exe scripts\windows\lifecycle.mjs stop
exit /b %errorlevel%
