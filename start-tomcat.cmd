@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-tomcat.ps1" %*
set "MINIOJ_EXIT=%ERRORLEVEL%"
if not "%MINIOJ_EXIT%"=="0" pause
exit /b %MINIOJ_EXIT%
