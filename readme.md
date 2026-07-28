# VeCTR

Your computer, in your pocket — over your local network. No cloud, no accounts, no subscriptions.

VeCTR pairs an Android companion app with a self-hosted desktop core (Node.js). The phone controls the laptop, transfers files and clipboard, captures notes and voice, and stays in sync even when offline.

---

## Getting Started

### 1. Desktop Core

**Requires:** Node.js 18+ (LTS recommended)

```bash
git clone <repo-url> && cd VeCTR
npm install
cp .env.example .env        # optional: configure LLM keys
npm start
```

The server prints one or more `VeCTR address` URLs (e.g. `http://192.168.1.42:4101`). It also advertises itself via mDNS as `_vectr._tcp` so the phone can discover it automatically.

Open `http://localhost:4101` in a browser for the admin console.

| Script | Command | Use case |
|--------|---------|----------|
| `npm start` | `node server/app.js` | Production |
| `npm run dev` | `nodemon server/app.js` | Development with auto-reload |

**Windows:** When prompted, allow Node.js through Windows Defender Firewall on **Private networks**.

### 2. Android App

**Option A — Install the APK**

Download from the [releases page](https://github.com/NRS-1605/Vectr/raw/main/releases/Vectr-debug.apk) and sideload it.

**Option B — Build from source**

```bash
cd android/AxonTest
./gradlew :app:assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

Open the project in Android Studio for development.

### 3. Connect

1. Connect your phone and laptop to the **same Wi-Fi network**.
2. Open the VeCTR app — it discovers the core automatically via mDNS.
3. If discovery fails (some networks block mDNS), go to **Settings**, enter the IP address and port (`4101`) printed by the server, and tap **Save**.

The home screen shows a green connection indicator when paired.

---

## Modules

### Control & Transfer

| Module | What it does |
|--------|-------------|
| **Touchpad** | Move cursor, left-click, right-click, scroll — uses `ydotool` (Linux) or `user32` (Windows) |
| **Macros** | Trigger configurable keypresses or shell commands on the laptop. Configure in the admin console. |
| **Files** | Upload from phone or download laptop files. Queues uploads while offline. |
| **Clipboard** | Send phone clipboard text to the laptop, or receive laptop clipboard on the phone. |

### Capture & Organization

| Module | What it does |
|--------|-------------|
| **Capture** | Save text notes, camera photos, or voice recordings. Voice is transcribed to text via local Whisper. |
| **Space** | Browse saved captures and lecture notes. Switch between flat notes view and subject-organized lectures. |
| **Lecture Pipeline** | Record lecture audio in Capture with Lecture Mode toggled on. The server transcribes, tags with subject/date/timetable slot, and saves as a per-subject Markdown file. Browse lectures by subject in Space. |
| **Todo** | Shared checklist synced via WebSocket. Works offline. |
| **Inventory** | Track food and medicine with expiry dates, quantities, and photos. |
| **Goals** | Organize goals into Weekly, Monthly, Half-yearly, and Yearly plans. Link sub-goals to parent goals. |
| **SchedWall** | Create recurring weekly schedules or one-off overlay events. The wallpaper view renders a live dark-blueprint HUD on your desktop. |

### Awareness & Focus

| Module | What it does |
|--------|-------------|
| **Telemetry** | Live CPU, RAM, temperature, and GPU usage from the laptop. |
| **News** | Headlines from configured RSS feeds. |
| **Focus Session** | Timed work sessions that earn **Berries** (local gamified reward balance). Choose Coastal (30 min), Open Waters (1 hr), or Uncharted (2 hr). |
| **Berries** | Virtual currency earned by completing focus sessions. Spent on premium features (Touchpad, Macros, Telemetry, Files, News). First use each day is free. |

---

## Voice & Lecture Pipeline

VeCTR uses a local [whisper.cpp](https://github.com/ggerganov/whisper.cpp) installation to transcribe voice recordings on the laptop — no internet required.

### Setup

```bash
git clone https://github.com/ggerganov/whisper.cpp.git ~/whisper.cpp
cd ~/whisper.cpp
bash models/download-ggml-model.sh small
make -j
```

The server locates `whisper-cli` at `~/whisper.cpp/build/bin/whisper-cli` and the model at `~/whisper.cpp/models/ggml-small.bin` by default. Override with `WHISPER_BINARY` and `WHISPER_MODEL` environment variables.

### Lecture Recording Flow

1. Open **Capture** on the phone.
2. Toggle **Lecture Mode** — the subject field auto-populates from your SchedWall timetable (matched by current day + hour).
3. Adjust subject/date if needed.
4. Tap the mic to record, tap again to stop.
5. The server transcribes and saves to `~/axon/lectures/<subject>/lecture-<date>.md` with YAML frontmatter.
6. Browse lectures by subject in **Space → Lectures** tab.

---

## Admin Console

The web admin console at `http://<laptop-ip>:4101` provides:

- **Status** — live WebSocket message log and connected devices
- **Capture** — manual text capture and recent capture list
- **Telemetry** — live CPU/RAM/temperature charts
- **Todo** — add and manage shared tasks
- **SchedWall** — one-off schedule overlays
- **Voyages** — view Berry balance and focus session history
- **Clipboard** — clipboard history from all devices
- **Settings** — macro buttons, LLM config (local Ollama or OpenAI), RSS feeds

---

## Storage Layout

```
~/axon/
├── captures/notes/           text captures and photos
│   └── attachments/          uploaded images
├── files/incoming/           files received from phone
│   └── outgoing/             files queued for phone download
├── inventory/photos/         inventory item photos
├── lectures/                 lecture transcriptions (per-subject)
│   ├── mathematics/
│   │   ├── lecture-2026-03-10.md
│   │   ├── lecture-2026-03-12.md
│   │   └── README.md
│   ├── computer-science/
│   └── ...
└── axon-core.sqlite          database (voyages, clipboard history, todos, etc.)
```

---

## Technical Architecture

### Server (`server/`)

| Layer | Technology |
|-------|-----------|
| HTTP | Express 4 — REST API on port 4101 |
| Real-time | WebSocket (`ws`) — subscriptions for touchpad, macros, telemetry |
| Database | SQLite (via better-sqlite3 bindings in `economy.js`) |
| Discovery | mDNS via `bonjour` — advertises as `_vectr._tcp` |
| Voice | Shells out to local `whisper.cpp` binary |
| Audio routing | `multer` for multipart file uploads |

The server is an Express app with static file serving (`public/`, `SchedWall/views/`) and a set of REST API routes all mounted under `/api`. The WebSocket server shares the same HTTP server on the same port, path `/ws`.

### Android Client (`android/AxonTest/`)

| Layer | Technology |
|-------|-----------|
| UI | XML layouts + Compose (Telemetry dashboard only) |
| Networking | OkHttp for REST + WebSocket |
| State | SharedPreferences for offline queues and local data |
| Architecture | Single-activity (`MainActivity`) with screen-based navigation via `ViewGroup` swapping. Singleton repository objects for data access. |

The app uses a foreground service (`VectrForegroundService`) to maintain the WebSocket connection. Offline queues (`OfflineCaptureQueue`, `SchedWallOfflineQueue`, `InventoryOfflineQueue`, `OfflineFileQueue`) buffer operations in SharedPreferences and flush them in order when connectivity returns.

### WebSocket Protocol (`shared/message-contract.js`)

Messages follow a uniform shape:

```json
{
  "type": "capture.new",
  "payload": { ... },
  "deviceId": "android-xxxx",
  "timestamp": 1711123456789
}
```

Subscriptions (touchpad, macro, telemetry) require a subscribe/unsubscribe handshake before sending commands, enforced by a per-client subscription set on the server. Feature usage is gated by the Berry economy — the server checks `checkAndChargeGate()` before activating a subscription.

### Key Design Decisions

- **No cloud relay.** All traffic stays on the LAN. Do not expose port 4101 to the internet.
- **Offline-first.** Each offline queue is a simple SharedPreferences JSON array flushed sequentially on reconnect.
- **Local Whisper.** Voice transcription runs on the laptop via whisper.cpp. No data leaves your network.
- **mDNS with manual fallback.** Bonjour handles discovery; if blocked, the user enters the IP manually.
- **Markdown storage.** Lecture notes are plain Markdown files with YAML frontmatter — readable and editable without VeCTR.

---

## Platform Support

| Feature | Linux | Windows |
|---------|-------|---------|
| Touchpad | `ydotool` (`/dev/uinput`) | `user32.dll` (via PowerShell) |
| Clipboard | `wl-clipboard` | `Get-Clipboard` / `Set-Clipboard` (PowerShell) |
| Daemon | `ydotoold` auto-spawned | Not required |
| mDNS | ✔ | ✔ |

The platform module (`server/platform/index.js`) selects the correct adapter at startup based on `process.platform`.

---

## Privacy & Security

- All communication stays on your local network.
- The server is unauthenticated by design for LAN use.
- Do not port-forward or expose port 4101 to the public internet.
- Shell macros execute arbitrary commands on the laptop — use only on trusted networks.
- Voice transcription is processed locally via whisper.cpp.

---

## Project Layout

```
VeCTR/
├── server/                Desktop core (Express + WebSocket)
│   ├── app.js             Entry point, route registration
│   ├── routes/            API route handlers
│   ├── platform/          Linux/Windows platform adapters
│   ├── ws/                WebSocket server
│   ├── economy.js         Berry economy + SQLite schema
│   ├── lecture-pipeline.js   Voice → transcript → Markdown
│   └── storage.js         Filesystem storage setup
├── android/AxonTest/      Android companion app
│   └── app/src/main/java/com/vectr/
│       ├── MainActivity.kt    Screen navigation + bindings
│       ├── DeviceWebSocket.kt Persistent WebSocket client
│       ├── *Repository.kt     REST/WebSocket data access
│       └── *Queue.kt          Offline operation queues
├── public/                Web admin console (HTML/CSS/JS)
├── SchedWall/             Schedule overlay admin + wallpaper
│   └── views/             admin.html, wallpaper.html
└── shared/                Message contract shared with server
```