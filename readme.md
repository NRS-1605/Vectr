<p align="center">
  <img src="android/AxonTest/app/src/main/res/drawable-nodpi/onyx_logo.png" width="110" alt="Onyx" />
</p>

<h1 align="center">ONYX</h1>

<p align="center">
  <a href="https://readme-typing-svg.demolab.com?font=EB+Garamond&size=24&pause=1000&color=E85D4E&center=true&vCenter=true&width=720&lines=Your+computer%2C+in+your+pocket.;No+cloud.+No+accounts.+No+subscriptions.;All+your+productivity+tools+unified."><img src="https://readme-typing-svg.demolab.com?font=EB+Garamond&size=24&pause=1000&color=E85D4E&center=true&vCenter=true&width=720&lines=Your+computer%2C+in+your+pocket.;No+cloud.+No+accounts.+No+subscriptions.;All+your+productivity+tools+unified." alt="Typing SVG" /></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-34A853?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Node.js-18+-339933?style=for-the-badge&logo=nodedotjs&logoColor=white" />
  <img src="https://img.shields.io/badge/WebSocket-ws-010101?style=for-the-badge&logo=socketdotio&logoColor=white" />
  <img src="https://img.shields.io/badge/SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white" />
  <img src="https://img.shields.io/badge/mDNS-bonjour-E85D4E?style=for-the-badge" />
  <img src="https://img.shields.io/badge/License-MIT-F5F0E8?style=for-the-badge&labelColor=2D2D2D" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Paper_Feel-%23F5F0E8?style=flat-square&labelColor=3D3D3D&color=F5F0E8" />
  <img src="https://img.shields.io/badge/Charcoal-%233D3D3D?style=flat-square&labelColor=F5F0E8&color=3D3D3D" />
  <img src="https://img.shields.io/badge/Coral-%23E85D4E?style=flat-square&color=E85D4E" />
  &nbsp;·&nbsp; <code>offline-first</code> · <code>LAN-only</code> · <code>local Whisper</code>
</p>

<div align="center">

<!-- Animated divider — pure SVG, no JS -->
<svg width="100%" height="2" viewBox="0 0 800 2" preserveAspectRatio="none" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#E85D4E" stop-opacity="0">
        <animate attributeName="stop-color" values="#E85D4E;#F5F0E8;#E85D4E" dur="4s" repeatCount="indefinite"/>
      </stop>
      <stop offset="50%" stop-color="#E85D4E">
        <animate attributeName="offset" values="0.3;0.7;0.3" dur="4s" repeatCount="indefinite"/>
      </stop>
      <stop offset="100%" stop-color="#E85D4E" stop-opacity="0"/>
    </linearGradient>
  </defs>
  <rect width="800" height="2" fill="url(#g)" opacity="0.9"/>
</svg>

</div>

> **Onyx pairs an Android companion app with a self-hosted desktop core (Node.js).** The phone becomes a remote for the laptop — touchpad, macros, files, clipboard, captures, todo, inventory, lectures — and stays in sync even when offline.

<details>
<summary><b>✨ Tap to see what it feels like</b></summary>
<br>

<p align="center">
  <img src="android/AxonTest/app/src/main/res/drawable-nodpi/vectr_gradient.png" width="100%" style="border-radius:16px; max-width:800px;" alt="Onyx gradient" />
</p>

```
  ●  FOCUS SESSION  25:00          ◀ swipe for day ▶
  08 AM  ──  Deep Work: Onyx polish
  09 AM  ──  Lecture: OS Threads
  12 AM  ──  MADE BY ILLUMINATI.PY  // monospace, faint
```

*Paper texture in light • Greyish charcoal in dark • White halftone dots • SchedWall as live wallpaper on desktop.*

</details>

---

## 🧭 Navigation

- **Home** — `Your Command Hub` hero above cards, last tile is **SchedWall**, footer `MADE BY ILLUMINATI.PY`
- **Tap any card** — slides + fades (`56dp` + `scale 0.98` → `300ms Decelerate`) — back to Home slides opposite

---

## ⚡ Quick Start

### 1 — Desktop Core

**Requires:** Node.js 18+ (LTS)

```bash
git clone https://github.com/NRS-1605/Vectr && cd Vectr
npm install
cp .env.example .env        # optional: LLM keys
npm start
```

Server prints `VeCTR address` URLs (e.g. `http://192.168.1.42:4101`) and advertises `_vectr._tcp` via mDNS.

Open `http://localhost:4101` for the admin console.

| Script | Command | Use |
|--------|---------|-----|
| `npm start` | `node server/app.js` | Production |
| `npm run dev` | `nodemon server/app.js` | Dev + auto-reload |

> **Windows:** Allow Node.js through *Windows Defender Firewall → Private networks*.

### 2 — Android App

**Option A — APK**

Download from [releases](https://github.com/NRS-1605/Vectr/raw/main/releases/Vectr-debug.apk) and sideload.

**Option B — Build**

```bash
cd android/AxonTest
./gradlew :app:assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

### 3 — Connect

1. Same Wi-Fi for phone + laptop
2. App discovers core via mDNS automatically
3. If blocked → **Settings** → enter `IP:4101` → **Save**

Home shows `● Connected` (coral) when paired.

---

## 🧩 Modules

### Control & Transfer

| Module | What it does | Icon |
|--------|--------------|:----:|
| **Touchpad** | Cursor, left/right click, scroll — `ydotool` (Linux) / `user32` (Windows) | 🖱️ |
| **Macros** | Trigger keypresses / shell commands (admin console) | ⌨️ |
| **Files** | Upload / download, queues while offline | 📁 |
| **Clipboard** | Push phone clipboard → laptop & pull laptop history | 📋 |

### Capture & Organization

| Module | What it does |
|--------|-------------|
| **Capture** | Text, camera, voice (Whisper on laptop) |
| **Space** | Browse captures + lectures — *fuzzy* search on heading/tag/preview/body |
| **Lecture Pipeline** | `Capture → Lecture Mode` → auto transcribes + saves `~/axon/lectures/<subject>/lecture-<date>.md` with YAML |
| **Todo** | Shared checklist — named lists, chips, checked sinks + strikethrough, offline queue |
| **Inventory** | Food/medicine with expiry, qty, photos — `SOONEST EXPIRY FIRST` |
| **Goals** | Weekly / Monthly / Half-yearly / Yearly, linked sub-goals |
| **SchedWall** | Weekly timetable + one-off overlays. **Wallpaper** is a dark blueprint HUD (<code>SchedWall/views/wallpaper.html</code>); **phone** is 16 hourly rows `8 AM — 12 AM`, swipe days, same `HOUR_PX` / `fmtHour` / `checked:${id}-${ymd}` as wallpaper |
| **Pomodoro** | `0h 25m` default — **Hours `0-12`** + **Minutes `0-59`** scroll pickers side-by-side, `59→0` bumps hour, `HH:MM:SS` / `MM:SS` display |
| **CGPA** | Semester grades |
| **Clipboard History** | From all devices |

---

## 🎙️ Voice & Lecture Pipeline

Local [`whisper.cpp`](https://github.com/ggerganov/whisper.cpp) — no internet.

```bash
git clone https://github.com/ggerganov/whisper.cpp.git ~/whisper.cpp
cd ~/whisper.cpp
bash models/download-ggml-model.sh small
make -j
```

Server looks for `~/whisper.cpp/build/bin/whisper-cli` + `~/whisper.cpp/models/ggml-small.bin` (override `WHISPER_BINARY` / `WHISPER_MODEL`).

**Flow:**

1. **Capture** → toggle **Lecture Mode** (subject auto-filled from SchedWall slot for now)
2. Mic → record → tap again
3. Server transcribes → `~/axon/lectures/<subject>/lecture-<date>.md`
4. **Space → Lectures** → browse by subject

---

## 🖥️ Admin Console

`http://<laptop-ip>:4101`

- **Status** — live WS log + devices
- **Capture / Todo / SchedWall / Clipboard / Settings** — manage boards, macros, LLM (Ollama / OpenAI)

---

## 🗂️ Storage Layout

```
~/axon/
├── captures/notes/           # text + photos
│   └── attachments/
├── files/incoming/           # from phone
│   └── outgoing/             # to phone
├── inventory/photos/
├── lectures/                 # per-subject Markdown
│   ├── mathematics/
│   │   ├── lecture-2026-03-10.md
│   │   └── README.md
│   └── computer-science/
└── axon-core.sqlite          # todos, inventory, clipboard, etc.
```

---

## 🏗️ Technical Architecture

### Server (`server/`)

| Layer | Tech |
|-------|------|
| HTTP | Express 4 — REST on `:4101` |
| Real-time | `ws` WebSocket (`/ws`) — `todos.update`, `schedwall.state` |
| DB | SQLite (`node:sqlite` in `db.js`) |
| Discovery | `bonjour` → `_vectr._tcp` |
| Voice | `whisper.cpp` via shell |
| Uploads | `multer` multipart |

Express + static (`public/`, `SchedWall/views/`) + ` /api` routes. WS shares same HTTP server.

### Android (`android/AxonTest/`)

| Layer | Tech |
|-------|------|
| UI | XML + `values` / `values-night` (`onyx_*` 31 keys) |
| Net | OkHttp REST + WS |
| State | `SharedPreferences` offline queues |
| Nav | Single `MainActivity` — `showScreen()` slide+fade (`56dp`/`0.98` scale, `300ms Decelerate` forward, `190ms Accelerate` back) + card press scale `0.96` |
| Theme | `Theme.Vectr` → `PREF_DARK_MODE` bool → `attachBaseContext` `uiMode NIGHT_YES/NO` — opening for `PREF_THEME` + `Theme.Vectr.Ocean` overlays |
| Service | `VectrForegroundService` — persistent WS; queues flush on reconnect |

### WebSocket Protocol (`shared/message-contract.js`)

```json
{
  "type": "capture.new",
  "payload": { "...": "..." },
  "deviceId": "android-xxxx",
  "timestamp": 1711123456789
}
```

Subscriptions (`macro`, `touchpad`) require subscribe handshake per-client.

### Key Decisions

- **LAN only** — never expose `:4101` publicly
- **Offline-first** — `SharedPreferences` JSON queues flushed sequentially
- **Local Whisper** — no data leaves LAN
- **mDNS + manual fallback** — bonjour + IP override
- **Markdown lectures** — YAML frontmatter, editable outside app

---

## 🎨 Theme

Centralized in `values/colors.xml` (31 `onyx_*`/`text_*`/`surface_*`):

- **Light** — Paper `#F5F0E8`, Charcoal `#3D3D3D` tiles → `on_card` light, `onyx_ink` dark, halftone charcoal dots
- **Dark** — Greyish `#1C1A18` bg, Charcoal tiles flip to `F0EBE2` *(shade of white)*, `on_card` dark, `onyx_ink` light, halftone white via `android:tint`

All cards/drawables use `@color/*` — adding a theme is a new `values-night-oled` / `Theme.Vectr.Ocean` palette + `PREF_THEME` branch in `attachBaseContext` (`MainActivity.kt:127`).

> **Opening for more themes:** add `Theme.Vectr.Amoled` / `Ocean` / `Forest` in `styles.xml` + palette in `values`, switch via `setTheme()` in `attachBaseContext`. Settings Switch → Spinner.

---

## 💅 Polish

- Home hero above cards, footer `MADE BY ILLUMINATI.PY` (`monospace` `10sp` `text_faint` centered)
- Wordmark `Onyx` `serif 30sp` `layout_gravity="start"` — left, not centered
- SchedWall phone: `8 AM — 12 AM` hourly slots (16) mirroring wallpaper (`HOUR_START=8`, `HOUR_END=24`, `fmtHour`), `box` `✓`, `time` faint, swipe day via `GestureDetector`
- No arrows on home tiles — clean
- Pomodoro `HH:MM:SS` + hour bar

---

## 📱 Platform Support

| Feature | Linux | Windows |
|---------|-------|---------|
| Touchpad | `ydotool` `/dev/uinput` | `user32.dll` (PowerShell) |
| Clipboard | `wl-clipboard` | `Get-Clipboard` / `Set-Clipboard` |
| Daemon | `ydotoold` auto-spawn | — |
| mDNS | ✔ | ✔ |

`server/platform/index.js` picks adapter via `process.platform`.

---

## 🔒 Privacy & Security

- LAN only, unauthenticated for local use — don't port-forward
- Shell macros → arbitrary commands — trusted networks only
- Voice → local `whisper.cpp`

---

## 📂 Project Layout

```
Vectr/
├── server/                # Express + WS
│   ├── app.js             # routes + static
│   ├── routes/            # API handlers
│   ├── platform/          # Linux/Windows
│   ├── ws/                # WebSocket
│   ├── db.js              # SQLite + schema
│   ├── lecture-pipeline.js
│   └── storage.js
├── android/AxonTest/      # Companion app
│   └── app/src/main/java/com/vectr/
│       ├── MainActivity.kt        # navigation + bindings
│       ├── DeviceWebSocket.kt     # WS client
│       ├── *Repository.kt
│       └── *Queue.kt
├── public/                # admin console
├── SchedWall/views/       # admin.html · wallpaper.html (grid blueprint)
└── shared/message-contract.js
```

---

<p align="center">
  <sub><code>MADE BY ILLUMINATI.PY</code> — paper feel, charcoal tiles, coral accent.</sub>
</p>
