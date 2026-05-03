# Progress Log

## 2026-05-03

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
