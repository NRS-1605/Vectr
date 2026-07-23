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

#### Windows (Command Prompt)

Install the current Node.js LTS release from [nodejs.org](https://nodejs.org/),
open **Command Prompt**, and run:

```bat
cd C:\path\to\Vectr
npm install
npm start
```

Keep that Command Prompt window open while using the Android app. When Windows
Defender Firewall asks, allow Node.js on **Private networks**. Then connect the
phone to the URL printed as `VeCTR address`; if automatic discovery does not
find the PC, enter that PC IP address and port `4101` in the app's Settings.

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

### 3. Use the remote touchpad

Open **Touchpad** in the Android app. In landscape orientation it has three
vertical controls:

- The large **touchpad area** on the left moves the desktop pointer. Tap it
  without moving to left-click.
- The narrow **scroll area** in the centre scrolls the active desktop window:
  drag up or down inside that strip.
- The **RIGHT CLICK** area on the right sends a right-click.

### 4. Configure macro buttons

Open the core's admin page at `http://<computer-ip>:4101`, find **Macro
Buttons**, and configure its eight buttons. Give each button a label, select
**keypress** as its type, enter a command from the relevant section below, and
press **Save Macros**. The buttons then appear on the phone's **Macros** screen.

`shell` macros run a command on the computer and should only be configured by
someone who trusts every phone that can reach the VeCTR core.

#### Keypress macros on Windows

Use familiar shortcut names, separated with `+` (spaces also work). You can
omit the optional `key ` prefix:

| Action | Command field value |
| --- | --- |
| Send Ctrl+Alt+T | `ctrl+alt+t` |
| Save | `ctrl+s` |
| Copy | `ctrl+c` |
| Paste | `ctrl+v` |
| Switch application | `alt+tab` |
| Press Enter | `enter` |
| Refresh | `f5` |
| Type text | `type Hello from VeCTR` |

Supported named keys include `ctrl`/`control`, `alt`, `shift`, `enter`, `esc`,
`tab`, `space`, the arrow keys (`up`, `down`, `left`, `right`), `delete`,
`backspace`, `home`, and `end`. Single letters and function keys such as `f5`
also work.

#### Keypress macros on Linux

Linux keypress macros use `ydotool` Linux input-event codes. A key command is a
space-separated list in the form `CODE:STATE`, where `1` presses a key and `0`
releases it. Release keys in reverse order so modifiers do not remain held.

For example, Ctrl+Alt+T is:

```text
29:1 56:1 20:1 20:0 56:0 29:0
```

This presses left Ctrl (`29`), left Alt (`56`), and T (`20`), then releases T,
Alt, and Ctrl. Other useful examples are:

| Action | Command field value |
| --- | --- |
| Save (Ctrl+S) | `29:1 31:1 31:0 29:0` |
| Copy (Ctrl+C) | `29:1 46:1 46:0 29:0` |
| Paste (Ctrl+V) | `29:1 47:1 47:0 29:0` |
| Press Enter | `28:1 28:0` |
| Refresh (F5) | `63:1 63:0` |
| Type text | `type Hello from VeCTR` |

To find the code for any shortcut, install `evtest`, run it with `sudo`, choose
your keyboard's `/dev/input/event...` device, and press each key. Its output
shows the required number, for example `code 29 (KEY_LEFTCTRL)` or `code 20
(KEY_T)`. Turn each pressed key into `code:1`, then add the same codes in
reverse order as `code:0`.

```bash
# Debian/Ubuntu
sudo apt install evtest
sudo evtest

# Arch
sudo pacman -S evtest
sudo evtest
```

`showkey --keycodes` can also display codes from a Linux virtual console. For
text instead of a shortcut, use `type ` followed by the text; this works on
both Linux and Windows.

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
PowerShell and Windows APIs. Start it with `npm start` from Command Prompt (as
shown above) and allow Node.js through Windows Defender Firewall on **private**
networks so your phone can reach the core. Keep the computer unlocked: Windows
can only send mouse and keyboard input to the active desktop session.

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
