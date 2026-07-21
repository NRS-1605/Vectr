# Windows SmartScreen will likely show "Windows protected your PC" for this unsigned indie executable.
# This is expected: select "More info" and then "Run anyway" after verifying the download source.

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Source = Join-Path $ScriptDir "dist\vectr-core-win.exe"
$InstallDir = Join-Path $env:LOCALAPPDATA "Vectr"
$Target = Join-Path $InstallDir "vectr-core.exe"

if (-not (Test-Path $Source)) {
    throw "Missing $Source. Run npm run build:dist first."
}

New-Item -ItemType Directory -Force -Path $InstallDir, "$env:USERPROFILE\axon-inbox", "$env:USERPROFILE\axon\captures\notes\attachments", "$env:USERPROFILE\axon\files\incoming", "$env:USERPROFILE\axon\files\outgoing", "$env:USERPROFILE\axon\inventory\photos" | Out-Null
Copy-Item -Force $Source $Target

$StartMenu = [Environment]::GetFolderPath("StartMenu")
$ShortcutPath = Join-Path $StartMenu "Programs\Vectr Core.lnk"
$Shell = New-Object -ComObject WScript.Shell
$Shortcut = $Shell.CreateShortcut($ShortcutPath)
$Shortcut.TargetPath = $Target
$Shortcut.WorkingDirectory = $InstallDir
$Shortcut.Description = "Start the VeCTR local desktop core"
$Shortcut.Save()

$Port = if ($env:PORT) { $env:PORT } else { "4101" }
Write-Host "VeCTR core installed at $Target"
Write-Host "Start Menu shortcut created: Vectr Core"
Write-Host "Admin console: http://localhost:$Port"
