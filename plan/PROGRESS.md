# Progress Log

## 2026-05-03

### Direction - Logging And Test Control
- Adapted Field Guide's testing nervous system in smaller Kotlin-native form.
- Keep one debug-only app endpoint, `GET /testing/state`, instead of a broad driver API.
- Use host debug-log capture plus ADB port setup for both S21 hardware and emulators.

### Research Notes - Field Guide Testing
- Field Guide runs a host debug-log server on `127.0.0.1:3947`.
- Field Guide runs an app-side loopback driver server and reaches it from the host with ADB port forwarding.
- Field Guide forwards device logs through Logcat and app logs through a structured debug server.
- Field Guide readiness is driven by a device-state snapshot plus a `stateMachine` JSON payload.
- Time Tracker should keep the pattern, not the scale: structured logs, one state endpoint, and simple scripts.

### Implementation Direction - Logging And Test Control
- Add `:core:logging` for structured, sanitized logs.
- Start a debug-only loopback endpoint from the app process when the app is debuggable.
- Query `GET /testing/state` through `adb forward tcp:4948 tcp:4948`.
- Drain debug app logs to the host through `adb reverse tcp:3947 tcp:3947`.
- Keep exact home coordinates out of logs by default.

### Open Items - Logging And Test Control
- Verify the debug endpoint on physical S21 and emulator after implementation.
- Decide later whether interactive UI-driving endpoints are needed; do not add them until a test flow requires them.

### Implementation - Logging And Test Control
- Added `:core:logging` with structured categories, sanitization, Logcat output, bounded in-memory logs, local file logging, and debug host-drain transport.
- Added a tiny host debug-log server under `tools/debug-server/server.js`.
- Added debug-device scripts for starting the log server, preparing ADB forward/reverse ports, and querying app state.
- Added debug-only `GET /testing/state` app endpoint that reports app snapshot, state-machine readiness, and recent sanitized logs.
- Wired logging through app startup, ViewModels, Room/DataStore repositories, notification setup, geofence receiver, activity receiver, and Play services adapters.
- Added tests for log sanitization and testing state-machine readiness.
- Verified with LIMP, Spotless, Detekt, `testDebugUnitTest`, Android lint, `assembleDebug`, and a host debug-server `/health` smoke test.
- Installed and launched the debug app on S21 `RFCNC0Y975L`.
- Verified `GET /testing/state` through ADB forward on S21; the endpoint returned `transportReady: true` and expected setup blockers.
- Verified app logs reached the host debug server through ADB reverse; `/logs/summary` reported lifecycle, app, and testing entries.

### Direction - Work Location Geofence
- Added work/job-site location to the product spec.
- Home and work/job-site geofence radii need separate user-configurable settings.
- Work/job-site geofence radius should support up to 5 miles when Android geofencing behavior is acceptable.
- Job-site driving should not count as tracked commute/away drive time.
- Activity Recognition drive buckets should be suppressed or separately reported while the user is inside the work/job-site geofence.

### Direction
- Chose native Kotlin Android instead of Flutter.
- App is an automatic timesheet, not a speed tracker.
- First work is project hygiene, planning, workflow, and architecture. No app code has been scaffolded yet.

### Implementation
- Created `plan/TODO.md` and `plan/PROGRESS.md`.
- Added reusable Git workflow files:
  - `.githooks/pre-commit`
  - `.githooks/commit-msg`
  - `scripts/git/pre-commit.ps1`
  - `scripts/git/valid-scopes.txt`
- The current `Time_Tracker` folder is not a Git repository. Hooks are ready to install after `git init` with `git config core.hooksPath .githooks`.

### Field Guide Audit
- Audited `C:\Users\rseba\Projects\Field_Guide_App`.
- Reviewed `AGENTS.md`, `.githooks/pre-commit`, `.claude/hooks/pre-commit.ps1`, `.git/hooks/commit-msg`, and `scripts/git/valid-scopes.txt`.
- Adapted Field Guide's useful workflow shape:
  - Keep durable planning and decisions in repo docs.
  - Use a shell shim for Git hook compatibility.
  - Use PowerShell for the pre-commit orchestrator on Windows.
  - Enforce conventional commit subjects, scope allowlist, narrative body, and `Reason:` trailer.
- Did not copy Dart/Flutter lint behavior. The Kotlin hook is Gradle-task based and expects Android/Kotlin tooling once the scaffold exists.

### Research Notes
- Android architecture guidance supports a UI layer plus data layer, with an optional domain layer for reusable or complex business logic: https://developer.android.com/topic/architecture
- Android architecture guidance recommends single source of truth and unidirectional data flow; this app will make the local database the app data source of truth.
- Android modularization guidance frames modules as loosely coupled, self-contained parts with clear purposes: https://developer.android.com/topic/modularization
- Android Kotlin style guidance requires focused source files, logical declaration order, standard formatting, four-space indentation, and no semicolons: https://developer.android.com/kotlin/style-guide
- Geofencing is the best automatic home-exit/home-enter trigger, but background geofence events can be delayed by platform limits: https://developer.android.com/develop/sensors-and-location/location/geofencing
- Activity Recognition Transition API is the right fit for drive vs idle buckets because it emits activity transition events for `IN_VEHICLE` and `STILL`: https://developer.android.com/develop/sensors-and-location/location/transitions

### Decisions
- Primary total: away-from-home time.
- Secondary buckets: drive, idle, unclassified.
- Workdays: weekly schedule.
- Storage: local only.
- Reports: in-app totals first.
- Notifications: default off; minimal/live notification reserved as settings later.
- MVP avoids route storage, continuous GPS tracking, and GPS-speed drive classification.
- Initial module layout:

```text
:app
:core:common
:core:database
:core:datastore
:core:location
:core:notifications
:core:testing
:feature:home
:feature:tracking
:feature:reports
:feature:settings
```

### Open Items
- Confirm exact app name.
- Confirm package name/application ID.
- Confirm whether CSV export is post-MVP or near-term.
- Confirm minimum SDK and target Android version strategy.
- Confirm whether the first scaffold should use Spotless with ktlint engine or ktlint Gradle plugin directly.

### Follow-Up Implementation
- Wired local repo to `https://github.com/RobertoChavez2433/Time-Tracker.git` as `origin`.
- Installed project hook path with `git config core.hooksPath .githooks`.
- Chose app name `Time Tracker` and application ID `com.robertochavez.timetracker`.
- Chose min SDK 26, target SDK 36, compile SDK 36.
- Added Gradle Kotlin DSL scaffold with these modules:

```text
:app
:core:common
:core:database
:core:datastore
:core:location
:core:notifications
:core:testing
:feature:home
:feature:tracking
:feature:reports
:feature:settings
```

- Added Gradle wrapper, version catalog, Compose Material 3, Hilt, Room, DataStore, Google Play services location/activity APIs, Spotless, and Detekt.
- Implemented core domain models: `HomeLocation`, `AwaySession`, `ActivityInterval`, `WorkSchedule`, `PayPeriodSettings`, and activity buckets.
- Implemented report math for daily, weekly, biweekly, monthly, and yearly totals, including midnight splitting and workday filtering.
- Implemented manual correction domain behavior for edited session windows, counts-toward-total toggles, and activity interval replacement.
- Implemented Room entities, DAOs, database, repositories, first migration registry, and Robolectric-backed repository tests.
- Implemented DataStore-backed app settings for disclosure and future notification preferences.
- Implemented location abstractions and Play services adapters for current precise location, home geofence registration, geofence broadcasts, and Activity Recognition transitions.
- Implemented notification channel/coordinator with default active notifications off.
- Implemented Compose app shell and Home, Tracking, Reports, and Settings screens.
- Added permission request/disclosure UI for precise/background location, activity recognition, and notifications.
- Added policy and manual testing docs:
  - `docs/PRIVACY.md`
  - `docs/MANUAL_DEVICE_TESTS.md`
  - `docs/RELEASE_POLICY.md`

### Verification
- `./gradlew.bat :core:common:test --console=plain` passed.
- `./gradlew.bat testDebugUnitTest --console=plain` passed.

### Notes
- Hilt was set to `2.57.2` because `2.59.2` requires AGP 9.0.0+, while this scaffold uses AGP 8.13.1 with Gradle 8.13.
- Room schema export was disabled after Room/KSP hit a Kotlin serialization compatibility issue in the local toolchain. The first migration registry exists as `TimeTrackerMigrations`.
- The Home screen implements map-pin adjustment as editable pin coordinates and radius. A tile-based map can be added later if a Google Maps API key is available.

### Direction Change
- Added miles driven as a required secondary reporting metric.
- Mileage must not weaken the privacy constraints. MVP mileage should be manual or odometer-style entry per away session.
- Automatic mileage from GPS speed, route geometry, or continuous GPS remains out of scope unless a future design explicitly changes the privacy model.

### Mileage Implementation
- Added `AwaySession.drivenMiles`.
- Persisted driven miles on `away_sessions`.
- Added manual mileage correction to the Tracking screen.
- Added driven miles to daily, weekly, biweekly, monthly, and yearly report UI models.
- Added mileage aggregation test coverage, including proportional midnight splitting.

### Final Verification
- Added `.gitignore` so Gradle, Kotlin, and Android build outputs stay out of the first commit.
- Added explicit runtime-permission guards around Play services current-location, geofence, and activity-recognition calls.
- Added coarse-location permission to the manifest and permission request flow because Android 12+ allows users to grant approximate location.
- Added a minimal launcher icon so the app manifest is release-ready enough for lint.
- `./gradlew.bat spotlessCheck detekt testDebugUnitTest lintDebug assembleDebug --console=plain` passed.

### Remaining Product Caveat
- Physical geofence and activity-recognition behavior still needs the manual device checklist because emulator/unit verification cannot prove real-world background delivery timing.

### Architecture Review
- Committed the initial working tree in logical slices: docs, Gradle/tooling, hook fixes, domain, persistence, location, and UI.
- Confirmed the broad module shape matches current Android guidance: UI/domain/data layering, local database as source of truth, unidirectional UI state, and Hilt modules available through app transitive dependencies.
- Tightened the main weak boundary: feature ViewModels no longer depend on concrete `:core:database` or `:core:datastore` classes.
- Added repository contracts in `:core:common`; Room/DataStore implementations are bound behind Hilt in their implementation modules.
- Added `scripts/quality/check-module-boundaries.ps1` and wired it into pre-commit so feature modules cannot regress back to persistence implementation imports.
- Current remaining implementation risk is not the Kotlin structure. It is release/device behavior: Android 11+ background-location UX, approximate-location implications for geofencing, and real-device geofence/activity verification.
- `./gradlew.bat spotlessCheck detekt testDebugUnitTest lintDebug assembleDebug --console=plain` passed after the DI boundary refactor.

### Architecture Test Support
- Added reusable fake repository implementations and a main-dispatcher JUnit rule in `:core:testing`.
- Added feature ViewModel unit tests for Home, Tracking, Reports, and Settings so feature logic can be verified through repository/location contracts instead of Room, DataStore, or Play services implementations.
- `./gradlew.bat spotlessApply --console=plain` passed.
- `./gradlew.bat testDebugUnitTest --console=plain` passed.

### Permission, Migration, CI, And Privacy Follow-Up
- Reworked Settings permissions into a staged flow: foreground tracking permissions first, then Android 11+ app-settings handoff for background location.
- Added precise/background location preflight failures before geofence registration so approximate-only or foreground-only grants produce an actionable UI message instead of silently failing.
- Added a Room migration test harness using the committed schema as the v1 baseline.
- Updated the v1 schema JSON to include driven miles after the migration test exposed that the schema snapshot was stale.
- Added a GitHub Actions Android workflow that runs module-boundary checks plus `spotlessCheck`, `detekt`, `testDebugUnitTest`, `lintDebug`, and `assembleDebug`.
- Added a delete-local-data flow in Settings, backed by a `LocalDataResetter` contract. The app implementation clears Room tables, resets DataStore settings, and unregisters geofence/activity automation.
- Decided to keep coordinate/radius home pin adjustment for MVP; a full map UI stays deferred until a Maps API key/product requirement exists.
- Decided CSV export is post-MVP unless explicitly requested before release.

### Remaining Blockers
- Physical-device verification is still required for real geofence latency, enter/exit/dwell, reboot/app restart, activity transitions, TalkBack/font-scale accessibility, and the Play background-location declaration video.
- Release signing still needs release keystore/signing direction.

### LIMP Policy Lock-In
- Researched the current standard tool layer for Kotlin/Android quality: Kotlin coding conventions, Android Kotlin style, ktlint standard rules, Detekt complexity rules, Android architecture, and Android modularization.
- Added `docs/LIMP_POLICIES.md` as the compact policy source for module boundaries, Kotlin source structure, and size/complexity budgets.
- Added `.editorconfig` so ktlint runs with explicit official Kotlin code style and a 140-character project line budget.
- Tightened Detekt's built-in Kotlin budgets for method length, class length, complexity, nesting, parameters, functions per file/class, and max line length.
- Added `scripts/quality/check-limp-policies.ps1` for the project-specific rules that ktlint/Detekt do not own: module dependencies, source imports, package-directory alignment, file length, and import counts.
- Replaced the CI and pre-commit module-boundary step with the broader LIMP policy gate; kept `check-module-boundaries.ps1` as a wrapper for compatibility.
- Kept `:core:common` plain Kotlin by changing its coroutine dependency from `kotlinx-coroutines-android` to `kotlinx-coroutines-core`.
- Wrapped long Kotlin declarations and queries exposed by the new 140-character Detekt limit.
- `./scripts/quality/check-limp-policies.ps1` passed.
- `./gradlew.bat spotlessCheck detekt --console=plain` passed.
