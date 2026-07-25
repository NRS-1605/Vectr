# VeCTR

> Your computer, in your pocket — privately, over your local network.

VeCTR pairs an Android companion app with a self-hosted desktop core. It lets a phone control, capture from, and stay in sync with a nearby laptop without accounts, cloud relays, or a subscription.

## Highlights

| Android companion | Desktop core |
| --- | --- |
| Touchpad, scrolling, left/right click | Linux and Windows input adapters |
| Configurable macro buttons | HTTP and WebSocket server on your LAN |
| Clipboard, capture, files, and notes | mDNS discovery with manual IP fallback |
| Todos, inventory, goals, focus, and Berries | Local storage under `~/axon` |
| Telemetry, news, SchedWall, and Capture Space | One Node.js process, default port `4101` |

## Offline first

VeCTR keeps useful work on the phone when the laptop is away. The app queues and syncs stateful work in order after it reconnects:

- Captures (text, photo, and voice)
- Todo changes
- SchedWall overlays
- Inventory entries and their photos
- File uploads
- Cached Berries balance, files, Todos, and inventory for offline viewing
- Local Goals, organised into weekly, monthly, half-yearly, and yearly plans

Desktop-control features such as Touchpad, Macros, Telemetry, and downloads naturally need the laptop to be online to perform their live action.

## Quick start

### 1. Run the desktop core

Install a current Node.js LTS release, then run:

```bash
npm install
npm start
```

The core prints one or more `VeCTR address` values. It also advertises itself as `_vectr._tcp` for phone discovery.

On Windows, allow Node.js through Windows Defender Firewall on **Private networks** when prompted. Keep the terminal open while using VeCTR.

### 2. Install the Android app

Download the [debug APK](https://github.com/NRS-1605/Vectr/raw/main/releases/Vectr-debug.apk), or build it locally:

```bash
cd android/AxonTest
./gradlew :app:assembleDebug
```

The APK is written to:

```text
android/AxonTest/app/build/outputs/apk/debug/app-debug.apk
```

Connect the phone and laptop to the same Wi-Fi network. VeCTR tries to discover the core automatically. To retry discovery, open **Settings** and press **Use Automatic Discovery**. If the network blocks mDNS, enter the printed desktop IP and port `4101` manually.

## Android modules

### Control and transfer

- **Touchpad** — move, click, right-click, and scroll the desktop.
- **Macros** — trigger configurable keypresses or trusted local shell commands.
- **Files** — upload immediately or queue uploads on the phone for later sync.
- **Clipboard** — send copied text to the laptop.

### Capture and organisation

- **Capture** — save text, camera photos, and voice notes into the desktop Capture Space.
- **Space** — browse saved captures.
- **Todo** — shared checklist that remains usable offline.
- **Inventory** — food and medicine tracker with expiry dates and photos.
- **Goals** — independent Weekly, Monthly, Half-yearly, and Yearly boards. Link a smaller goal to a larger parent goal and tap any goal to mark it complete.
- **SchedWall** — create schedule overlays; unsent overlays queue locally.

### Awareness and focus

- **Telemetry** — live CPU, RAM, and temperature data.
- **News** — headlines from configured feeds.
- **Focus Session** — timed focus work that earns Berries.
- **Berries** — local reward balance, cached on the phone for offline display.

## Macro configuration

Open the desktop admin page at `http://<computer-ip>:4101`, configure the Macro Buttons, then open **Macros** in the phone app.

Shell macros execute on the laptop. Only configure them on a trusted private network.

### Windows keypress examples

| Action | Value |
| --- | --- |
| Save | `ctrl+s` |
| Copy | `ctrl+c` |
| Paste | `ctrl+v` |
| Switch application | `alt+tab` |
| Enter | `enter` |
| Type text | `type Hello from VeCTR` |

### Linux keypress examples

Linux uses `ydotool` input-event codes in `CODE:STATE` form. Press with `1`, release with `0`, releasing modifiers in reverse order.

| Action | Value |
| --- | --- |
| Save | `29:1 31:1 31:0 29:0` |
| Copy | `29:1 46:1 46:0 29:0` |
| Paste | `29:1 47:1 47:0 29:0` |
| Enter | `28:1 28:0` |

Install `ydotool` and `wl-clipboard` through your distribution package manager. The user running VeCTR needs access to `/dev/uinput`.

## SchedWall

SchedWall is bundled into the core:

- Admin: `http://<computer-ip>:4101/schedwall/admin`
- Wallpaper: `http://<computer-ip>:4101/schedwall/wallpaper`

## Storage

Runtime data remains local:

```text
~/axon/
├── captures/             notes and attachments
├── files/                transferred files
└── inventory/photos/     inventory item photos
```

The server database is `server/axon-core.sqlite`. Use [server/config.example.json](server/config.example.json) as the starting point for a clean configuration.

## Project layout

```text
android/AxonTest/     Android companion app
server/               axon-core HTTP, WebSocket, storage, and routes
server/platform/      Linux and Windows integrations
public/               desktop admin UI
SchedWall/            SchedWall admin and wallpaper pages
shared/               shared message contract
```

## Development

```bash
npm run dev
```

Open `android/AxonTest` directly in Android Studio to develop the Android client.

## Privacy and safety

VeCTR is intended for a trusted private LAN. Do not expose port `4101` to the public internet or port-forward it: anyone who can reach an unprotected core may access desktop-control features.
