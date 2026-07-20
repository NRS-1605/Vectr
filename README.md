# VeCTR desktop core

The Android app discovers any VeCTR core on the local network through the same
`_vectr._tcp` service. It can therefore connect to a Windows computer or a
Linux computer (including Arch) without changing the phone app.

## Start

On either desktop platform, install a current Node.js LTS release, then run:

```bash
npm install
npm start
```

`npm start` selects the desktop adapter automatically and starts the HTTP,
WebSocket, mDNS, clipboard, and input services together. The phone should find
the computer automatically; manual IP setup remains available in Settings.

## SchedWall

SchedWall is now part of the same axon-core process and port—do not start the
separate `SchedWall/server.js`. Open the integrated admin-console **SchedWall**
tab, or visit `/schedwall/admin`; the live wallpaper is at
`/schedwall/wallpaper`. The Android Home tab has a SchedWall card for creating
one-off dated overlays. Events entered while disconnected are stored on the
phone and synced automatically after it reconnects.

## Platform structure

```
server/platform/
  linux/     Wayland clipboard and ydotool input adapter
  windows/   Native PowerShell clipboard and Windows input adapter
```

Both adapters expose the same protocol, so captures, files, todos, macros,
touchpad, telemetry, and clipboard sync work with the same Android build.

## Linux setup

Install `wl-clipboard` and `ydotool` once using your distribution package
manager. VeCTR starts `ydotoold` itself when `npm start` runs, so it no longer
needs to be started in a separate terminal. Your user still needs permission to
access `/dev/uinput`, which is normally configured by the package or udev rule.

Set `VECTR_MANAGE_YDOTOOL=false` only if you intentionally manage an existing
`ydotoold` service yourself.

## Windows setup

No input helper or clipboard utility is required. The Windows adapter uses the
built-in PowerShell and Windows APIs. Allow Node.js through Windows Defender
Firewall on private networks when prompted so the Android phone can connect.
For optional voice transcription, install or build whisper.cpp and set
`WHISPER_BINARY` and `WHISPER_MODEL` in `.env`; the default Windows executable
location is `~/whisper.cpp/build/bin/whisper-cli.exe`.
