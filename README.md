# VeCTR

> Your computer, in your pocket.

VeCTR is a self-hosted Android companion and local desktop core for controlling,
organising, and staying in sync with your computer from your phone. It runs on
your private network—there is no cloud account, relay server, or subscription
required.

<p align="center">
  <strong>Touchpad · Macros · Files · Clipboard · Captures · Focus · Inventory</strong>
</p>

## What it does

| From your Android phone | On your desktop core |
| --- | --- |
| Remote touchpad, click, right-click, and scroll | Linux (including Arch) and Windows support |
| Trigger configurable macros | mDNS discovery, with manual IP fallback |
| Send clipboard text and transfer files | One Node.js process on one local port |
| Capture text, photos, and voice notes | Local SQLite data and folders under `~/axon` |
| View CPU, RAM, and temperature telemetry | Built-in clipboard, input, and storage adapters |
| Shared Todos, news, and capture space | Integrated SchedWall admin and wallpaper routes |
| Focus sessions, Berry economy, and smart inventory | Android-native UI and offline sync queues |

## Quick start

### 1. Start the desktop core

Install a current Node.js LTS release, then run this in the project root:

```bash
npm install
npm start
```

VeCTR automatically selects its Linux or Windows adapter, starts HTTP,
WebSocket, mDNS, clipboard, and input services together, and prints the LAN
address for manual connection if discovery is unavailable.

### 2. Install the Android app

Download the latest [VeCTR Android APK](https://github.com/NRS-1605/Vectr/raw/main/releases/Vectr-debug.apk) to your Android phone, open it, and allow installs from your browser or file manager if Android asks. This is a debug build, so Android may show a standard debug-app warning.

To build the same APK yourself:

```bash
cd android/AxonTest
./gradlew :app:assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`.
The app discovers VeCTR cores advertised as `_vectr._tcp`; choose the detected
computer, or enter its IP and port in Settings.

## Desktop support

### Linux

VeCTR supports Linux desktops, including Arch. Install `wl-clipboard` and
`ydotool` using your distribution’s package manager. `npm start` launches
`ydotoold` when available; your user must still have access to `/dev/uinput`
(normally handled through the package’s udev setup).

Set `VECTR_MANAGE_YDOTOOL=false` only when you deliberately run `ydotoold`
yourself.

### Windows

No third-party input or clipboard service is needed. VeCTR uses native
PowerShell and Windows APIs. When Windows Defender Firewall asks, allow Node.js
on **private** networks so your phone can reach the core.

## Storage and configuration

Runtime data stays out of the repository and is kept locally:

```text
~/axon/
├── captures/             # notes and attachments from the phone
├── files/                # incoming and outgoing transfers
└── inventory/photos/     # smart-inventory item photos
```

The backend database is `server/axon-core.sqlite`. Editable settings are stored
in `server/config.json`; use [server/config.example.json](server/config.example.json)
as a reference for a clean default configuration.

## Install without Node.js

End users only need the appropriate installer script. It downloads the matching
binary from the [latest VeCTR release](https://github.com/NRS-1605/Vectr/releases/latest),
installs it for the current user, and creates its data folders.

### Linux x64

```bash
curl -fsSL https://raw.githubusercontent.com/NRS-1605/Vectr/main/install.sh | sh
```

The core installs to `~/.local/bin/vectr-core` and adds that directory to your
Bash `PATH` automatically. Run `source ~/.bashrc` once in the current terminal,
then start `vectr-core` and open `http://localhost:4101`.

### Windows x64

Download [install.ps1](https://raw.githubusercontent.com/NRS-1605/Vectr/main/install.ps1),
then run it from PowerShell:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\install.ps1
```

It installs to `%LOCALAPPDATA%\Vectr`, creates a **Vectr Core** Start Menu
shortcut, and prints the admin URL. Windows SmartScreen may show an unsigned
binary warning; use **More info → Run anyway** only after verifying the
download source.

## Building release artifacts

For maintainers, build native, Node.js-free desktop executables for Linux x64
and Windows x64:

```bash
npm run build:dist
```

This produces `dist/vectr-core-linux` and `dist/vectr-core-win.exe`. Upload
both files to a GitHub Release with those exact names; the installers above use
GitHub’s `releases/latest/download` endpoint. The bundled core keeps static
admin pages inside the executable and stores mutable state in `~/axon` (or
`%USERPROFILE%\\axon` on Windows).

## SchedWall

SchedWall is integrated into the same VeCTR core—no second server or port is
required.

- Admin console: `http://<computer-ip>:4101/schedwall/admin`
- Wallpaper: `http://<computer-ip>:4101/schedwall/wallpaper`

Events created while the phone is offline are queued locally and synced once it
reconnects.

## Project layout

```text
android/AxonTest/     Android app
server/               axon-core HTTP, WebSocket, SQLite, and API routes
server/platform/      Linux and Windows desktop adapters
public/               desktop admin console
SchedWall/            integrated SchedWall pages
shared/               message contract shared by backend clients
```

## Privacy and network safety

VeCTR is designed for a trusted private LAN. It intentionally has no remote
account or public exposure layer. Do not port-forward port `4101` or expose it
to the public internet; anyone who can reach an unprotected core could use its
desktop-control features.

## Development

```bash
npm run dev
```

The Android project can be opened directly from `android/AxonTest` in Android
Studio. Generated build output, local databases, runtime data, per-machine
settings, and secrets are excluded by [.gitignore](.gitignore).

---

Built for the moments when your computer is across the room—not across the
internet.
