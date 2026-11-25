@echo off
setlocal
cd /d "%~dp0"
runtime\node.exe scripts\windows\lifecycle.mjs backup
exit /b %errorlevel%
