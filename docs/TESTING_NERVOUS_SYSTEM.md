# Testing Nervous System

Time Tracker uses a small Field Guide-inspired verification loop:

- App logs go through `:core:logging`.
- Debug builds expose one app-side loopback endpoint: `GET /testing/state`.
- A host debug-log server captures structured app logs on `127.0.0.1:3947`.
- ADB port setup works for either the S21 or an emulator.

## Local Device Setup

Start the host log server:

```powershell
.\scripts\testing\start-debug-server.ps1
```

Prepare ports for a connected device:

```powershell
.\scripts\testing\prepare-device-ports.ps1 -DeviceId RFCNC0Y975L
```

For an emulator, pass its adb id instead, for example:

```powershell
.\scripts\testing\prepare-device-ports.ps1 -DeviceId emulator-5554
```

Install and start the debug app, then query state:

```powershell
.\gradlew.bat installDebug
adb -s RFCNC0Y975L shell am start -n com.robertochavez.timetracker/.MainActivity
.\scripts\testing\get-state.ps1 -RunId manual-s21 -ActorId S21
```

## Endpoint Contract

`GET /testing/state` returns:

- `transportReady`: whether the HTTP endpoint responded.
- `snapshot`: current app facts such as home setup, active session, report inputs, settings, and recent log count.
- `stateMachine`: readiness posture, interaction readiness, and blockers.
- `recentLogs`: the most recent sanitized structured app logs.

The endpoint starts only when Android marks the app debuggable. It is not intended for release builds.

The host log server exposes `GET /health`, `POST /clear`, `GET /logs`, `GET /logs/errors`, and `GET /logs/summary`.
