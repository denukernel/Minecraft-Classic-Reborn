@echo off
setlocal
where pwsh.exe >nul 2>&1
if errorlevel 1 (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-runnable-jar.ps1" %*
) else (
    pwsh.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-runnable-jar.ps1" %*
)
exit /b %errorlevel%
