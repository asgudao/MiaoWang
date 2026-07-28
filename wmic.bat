@echo off
if "%1"=="process" (
  REM wmic process wrapper - use PowerShell Get-Process
  powershell -Command "$p = Get-Process -Name 'HBuilderX' -ErrorAction SilentlyContinue; if ($p) { $p | ForEach-Object { Write-Output \"`r`n`r`nNode,ExecutablePath`r`n$($_.Id),$($_.Path)\" } }"
) else (
  REM Unsupported wmic command
  echo process - Alias not found.
  exit /b 1
)