# Testing Nervous System

Time Tracker uses a small Field Guide-inspired verification loop:

- App logs go through `:core:logging`.
- Debug builds expose one app-side loopback endpoint only when built with `-PtimeTracker.e2eDebug=true`: `GET /testing/state`.
- A host debug-log server captures structured app logs on `127.0.0.1:3947`.
- ADB port setup works for either the S21 or an emulator.
- `tools/testing/Invoke-E2EVerification.ps1` drives named UI subflows through stable Compose test tags, captures artifacts, and validates state after feature actions.

## Full E2E Flow

Run the device verification flow against the only connected device:

```powershell
.\tools\testing\Invoke-E2EVerification.ps1
```

Run it against a specific device:

```powershell
.\tools\testing\Invoke-E2EVerification.ps1 -DeviceId RFCNC0Y975L
```

The script:

- Installs the debug app with `-PtimeTracker.e2eDebug=true` and package `com.robertochavez.timetracker.debug`.
- Starts the host debug-log server.
- Prepares ADB forward/reverse ports.
- Resets local app data through the Settings UI confirmation dialog.
- Exercises Settings, Locations, Tracking, Reports, and bottom navigation UI controls.
- Confirms `/testing/state` changes after saves, switches, session start/stop, manual correction, report navigation, relaunch, and destructive reset.
- Captures `persisted-baseline-state.json`, force-stops/relaunches the app, captures `after-relaunch-state.json`, and writes `persistence-comparison.json`.
- Fails if the host debug server captures app error logs.
- Writes screenshots, UI hierarchy dumps, state snapshots, debug-log extracts, `summary.json`, `report.md`, and `button-state-matrix.json` under `tools/testing/test-results/<date>/<run-id>/`.

Named subflows:

- `bootstrap_reset`
- `settings_tracking_setup`
- `settings_timesheet_rules`
- `home_location_controls`
- `tracking_session_correction`
- `reports_navigation_and_totals`
- `bottom_nav_switching`
- `persistence_relaunch`
- `destructive_reset_confirmation`
- `debug_log_audit`

The markdown report includes flow results, a button matrix summary, persistence comparison pointers, a premium dark olive visual checklist, and debug-log audit artifacts.

Buttons that intentionally leave the app for Android system screens can be probed with:

```powershell
.\tools\testing\Invoke-E2EVerification.ps1 -DeviceId RFCNC0Y975L -ProbeSystemButtons
```

## Local Device Setup

The lower-level scripts are useful when debugging the harness manually.

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
.\gradlew.bat -PtimeTracker.e2eDebug=true installDebug
adb -s RFCNC0Y975L shell am start -n com.robertochavez.timetracker.debug/com.robertochavez.timetracker.MainActivity
.\scripts\testing\get-state.ps1 -RunId manual-s21 -ActorId S21
```

## Endpoint Contract

`GET /testing/state` returns:

- `transportReady`: whether the HTTP endpoint responded.
- `debugHarness`: whether the app is debuggable and the E2E debug flag was enabled at build time.
- `snapshot`: current app facts such as home setup, active session, report inputs, settings, and recent log count.
- `snapshot.reportTotals`: daily, weekly, biweekly, monthly, and yearly totals used for E2E report/persistence checks.
- `stateMachine`: readiness posture, interaction readiness, and blockers.
- `recentLogs`: the most recent sanitized structured app logs.

The endpoint starts only when Android marks the app debuggable and `-PtimeTracker.e2eDebug=true` was used for the build. It is not available in release builds.

The host log server exposes `GET /health`, `POST /clear`, `GET /logs`, `GET /logs/errors`, and `GET /logs/summary`.
