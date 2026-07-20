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

Build the debug APK:

```bash
cd android/AxonTest
./gradlew :app:assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` on your Android phone.
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
