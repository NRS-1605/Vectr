# Windows SmartScreen will likely show "Windows protected your PC" for this unsigned indie executable.
# This is expected: select "More info" and then "Run anyway" after verifying the download source.

$ErrorActionPreference = "Stop"
$Repository = "NRS-1605/Vectr"
$SourceUrl = if ($env:VECTR_BINARY_URL) { $env:VECTR_BINARY_URL } else { "https://github.com/$Repository/releases/latest/download/vectr-core-win.exe" }
$InstallDir = Join-Path $env:LOCALAPPDATA "Vectr"
$Target = Join-Path $InstallDir "vectr-core.exe"

New-Item -ItemType Directory -Force -Path $InstallDir, "$env:USERPROFILE\axon-inbox", "$env:USERPROFILE\axon\captures\notes\attachments", "$env:USERPROFILE\axon\files\incoming", "$env:USERPROFILE\axon\files\outgoing", "$env:USERPROFILE\axon\inventory\photos" | Out-Null
Write-Host "Downloading VeCTR core…"
Invoke-WebRequest -Uri $SourceUrl -OutFile $Target

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
