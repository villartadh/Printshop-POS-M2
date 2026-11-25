@echo off
setlocal
cd /d "%~dp0"
runtime\node.exe scripts\windows\lifecycle.mjs uninstall
exit /b %errorlevel%
