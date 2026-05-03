# Time Tracker TODO

Last refreshed: 2026-05-03.

Status legend:
- `[ ]` not started
- `[~]` in progress
- `[x]` done
- `[!]` blocked or needs decision

## Project Hygiene First

Working standards before app code:
- Kotlin source should be small, focused, and easy to review. Avoid large utility files, global mutable state, and cross-feature shortcuts.
- Use feature-first modularity from the first scaffold. Each module gets a narrow public API and explicit dependencies.
- Keep domain models and rules plain Kotlin where possible. Android framework APIs should be behind interfaces so domain logic can be tested without a device.
- Tests are required for core time math, report totals, activity bucket aggregation, and manual correction behavior before relying on geofence or activity-recognition device behavior.
- Follow the Android Kotlin style guide as the baseline. Enforce formatting through Gradle, not reviewer memory.
- Commit messages use conventional commits. `feat`, `fix`, `refactor`, and `perf` require scopes, a body, and a `Reason:` trailer.

Checklist:
- [x] Create `plan/TODO.md` and `plan/PROGRESS.md`.
- [x] Add a short working standards section before app code exists.
- [x] Require small, readable Kotlin classes/functions/methods.
- [x] Require feature-first modularity from the first scaffold.
- [x] Require tests for domain logic before relying on Android device behavior.
- [x] Establish commit-message rules before first substantive commit.
- [x] Establish lint/format/test commands before feature work.
  - Target commands once the Gradle scaffold exists: `./gradlew spotlessCheck` or `./gradlew ktlintCheck`, `./gradlew detekt` or `./gradlew lintDebug`, and `./gradlew testDebugUnitTest` or `./gradlew test`.
  - Hook templates are in `.githooks/` and `scripts/git/`. Install after `git init` with `git config core.hooksPath .githooks`.

## Kotlin/Android Architecture Research

Sources:
- Android app architecture: https://developer.android.com/topic/architecture
- Android modularization: https://developer.android.com/topic/modularization
- Android Kotlin style guide: https://developer.android.com/kotlin/style-guide
- Geofencing: https://developer.android.com/develop/sensors-and-location/location/geofencing
- Activity Recognition Transition API: https://developer.android.com/develop/sensors-and-location/location/transitions

Architecture lock:
- Native Kotlin Android app.
- Jetpack Compose and Material 3 for UI.
- Hilt for dependency injection.
- Room for local persistence.
- DataStore for preferences/settings.
- Coroutines and Flow for async state and data streams.
- WorkManager only where deferred/retryable background work is actually needed.
- Google Play services Location APIs for geofencing and Activity Recognition transitions.
- UI/data/domain layering follows Android guidance: UI layer, data layer, optional domain layer where reusable or complex business logic justifies it.
- Unidirectional data flow is the default for UI state and user events.
- Local database is the source of truth for app data.

Initial module layout:

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

Module rules:
- `:app` wires navigation, DI, app startup, and Android entry points.
- `:core:common` owns plain Kotlin primitives, time helpers, result types, and shared domain abstractions that are not feature-specific.
- `:core:database` owns Room entities, DAOs, migrations, and local persistence implementations.
- `:core:datastore` owns preference schemas and settings persistence.
- `:core:location` owns geofence and activity-recognition adapters behind interfaces.
- `:core:notifications` owns notification channels, permission-aware notification helpers, and future live timer notification support.
- `:core:testing` owns fakes, fixture builders, coroutine test utilities, and reusable test helpers.
- `:feature:*` modules own Compose screens, ViewModels, feature-specific UI state, and feature navigation entry points.
- Domain models stay plain Kotlin unless Android integration is unavoidable.
- Android framework APIs are isolated behind interfaces for testability.

Checklist:
- [x] Use native Kotlin, Jetpack Compose, Material 3, Room, DataStore, Hilt, Coroutines, Flow, WorkManager where needed, and Google Play services Location/Activity Recognition.
- [x] Follow Android's recommended UI/domain/data layering.
- [x] Follow Android modularization guidance: loosely coupled modules with narrow public APIs.
- [x] Use Google Android Kotlin style as the baseline.
- [x] Decide initial module layout before scaffolding.

## Field Guide Workflow Audit

Field Guide source audited: `C:\Users\rseba\Projects\Field_Guide_App`.

Findings to adapt:
- Field Guide treats agent workflow docs as a single source of truth and uses planning docs to preserve durable decisions.
- Field Guide's `.githooks/pre-commit` is a small shell wrapper that delegates to PowerShell for Windows compatibility.
- Field Guide's PowerShell pre-commit orchestrator runs analysis, custom lint/grep checks, and targeted tests.
- Field Guide's `commit-msg` hook enforces conventional commit shape, scope allowlists, narrative bodies for substantive commits, and a `Reason:` trailer.
- Field Guide's Dart/Flutter checks are not portable to this app. The useful pattern is the staged-file gate and layered quality checks, not the Dart-specific rules.

Checklist:
- [x] Audit `C:\Users\rseba\Projects\Field_Guide_App`.
- [x] Review Field Guide's `AGENTS.md` workflow rules.
- [x] Review `.githooks/pre-commit`.
- [x] Review `.claude/hooks/pre-commit.ps1`.
- [x] Review `.git/hooks/commit-msg`.
- [x] Review `scripts/git/valid-scopes.txt`.
- [x] Adapt commit-message policy:
  - Use conventional commits.
  - Require scopes for `feat`, `fix`, `refactor`, and `perf`.
  - Require a body and `Reason:` trailer for substantive commits.
- [x] Adapt hook behavior for Kotlin:
  - Run formatting check.
  - Run static analysis.
  - Run unit tests relevant to staged Kotlin files where practical.
- [x] Do not blindly copy Dart/Flutter lint rules; translate the intent to Kotlin tooling.

## Android App Scaffold

- [x] Create a Kotlin Android project with Gradle Kotlin DSL.
- [x] Add Gradle wrapper.
- [x] Add version catalog.
- [x] Add Compose Material 3.
- [x] Add Hilt.
- [x] Add Room.
- [x] Add DataStore.
- [x] Add Google Play services location/activity dependencies.
- [x] Add ktlint or Spotless formatting.
- [x] Add Detekt or equivalent static analysis.
- [x] Add git hooks adapted from Field Guide.
- [x] Add valid commit scopes for this app.

Commit scopes:

```text
app
architecture
core
database
datastore
domain
git
home
location
notifications
permissions
privacy
release
reports
settings
tests
tooling
tracking
ui
```

## Core Tracking Domain

- [x] Model `HomeLocation`.
- [x] Model `AwaySession`.
- [x] Model `ActivityInterval`.
- [x] Model `WorkSchedule`.
- [x] Model `PayPeriodSettings`.
- [x] Model driven miles as a secondary reporting metric.
- [x] Support manual miles driven entry per away session.
- [x] Support odometer-style start/end entry or direct miles entry in the UI.
- [x] Model activity buckets:
  - `DRIVE`
  - `IDLE`
  - `UNCLASSIFIED`
- [x] Primary timesheet total is total away-from-home time.
- [x] Drive/idle/unclassified are secondary reporting buckets.
- [x] Miles driven are a secondary reporting metric, not the primary timesheet total.

## Location + Activity Automation

- [x] Let user set home by current precise location.
- [x] Let user set or adjust home by map pin.
- [x] Register a home geofence.
- [x] On geofence exit, start an away session only if the day is trackable.
- [x] On geofence enter/dwell, stop the active away session.
- [x] Use Activity Recognition Transition API for `IN_VEHICLE` and `STILL`.
- [x] Do not use GPS speed for drive classification.
- [x] Do not calculate mileage from GPS speed.
- [x] Do not store route geometry for mileage.
- [x] MVP mileage is manual/odometer-style entry unless an explicit future automatic-mileage design is approved.
- [x] Do not store routes.
- [x] Do not run a continuous GPS tracker for MVP.
- [x] Default notification behavior is no active notification.
- [x] Keep future settings planned for minimal active notification and live timer notification.

## Local Persistence

- [x] Persist home location locally.
- [x] Persist active and completed away sessions locally.
- [x] Persist activity intervals locally.
- [x] Persist driven miles locally with away sessions.
- [x] Persist work schedule locally.
- [x] Persist pay period settings locally.
- [x] Add Room migrations from the first persisted schema.
- [x] Keep route/location history out of persistence for MVP.

## UI

- [x] Add app shell and navigation.
- [x] Add home setup screen.
- [x] Add current status/today summary screen.
- [x] Add tracking/session detail screen.
- [x] Add reports screen.
- [x] Add settings screen.
- [x] Add permission education and request flow for precise/background location and activity recognition.

## Reports

- [x] Show daily totals.
- [x] Show weekly totals.
- [x] Show biweekly totals.
- [x] Let user choose biweekly anchor start date.
- [x] Show monthly totals.
- [x] Show yearly totals.
- [x] Show miles driven in daily, weekly, biweekly, monthly, and yearly reports.
- [x] Split sessions across midnight by calendar day.
- [x] Ignore non-workdays automatically.
- [x] Show unclassified time separately.

## Manual Corrections

- [x] Allow editing session start/end.
- [x] Allow toggling whether a session counts toward work totals.
- [x] Allow correcting drive/idle/unclassified intervals or totals.
- [x] Allow correcting miles driven.
- [x] Mark edited sessions as manually adjusted.

## Testing

- [x] Unit test time-splitting across midnight.
- [x] Unit test workday filtering.
- [x] Unit test daily/weekly/biweekly/monthly/yearly totals.
- [x] Unit test manual edits updating reports.
- [x] Unit test activity bucket aggregation.
- [x] Unit test mileage aggregation.
- [x] Integration test Room repositories.
- [x] Add fake location/activity adapters for domain tests.
- [x] Add manual device test checklist for geofence behavior.

## Release/Policy Readiness

- [x] Write privacy notes before background location is implemented.
- [x] Document why background location is needed for automatic home enter/exit detection.
- [x] Add user-facing disclosure for background location before release builds.
- [x] Add user-facing disclosure for activity recognition before release builds.
- [x] Verify Play policy requirements before a Play Store release track.
- [x] Keep local-only data handling as the default unless the product direction changes.

## Architecture Review Follow-Ups

Review result: aligned in the broad shape, with one boundary tightened during review.

- [x] Verify current Android architecture guidance for layering, single source of truth, and unidirectional data flow.
- [x] Verify Android modularization guidance for low coupling, clear APIs, and strict visibility.
- [x] Verify Hilt multi-module guidance: the app module must have Hilt modules and injected classes in transitive dependencies.
- [x] Remove feature-module dependencies on `:core:database`.
- [x] Remove feature-module dependencies on `:core:datastore`.
- [x] Add repository contracts in `:core:common` so feature ViewModels depend on interfaces.
- [x] Bind Room-backed repository implementations behind Hilt in `:core:database`.
- [x] Bind DataStore-backed settings implementation behind Hilt in `:core:datastore`.
- [x] Keep Android framework and Play services calls isolated behind injectable interfaces.
- [x] Add a module-boundary quality script to block feature imports of persistence implementation modules.
- [x] Wire the module-boundary quality script into the pre-commit Kotlin/Android gate.
- [x] Replace deprecated `hiltViewModel` imports with the current package.
- [x] Add feature ViewModel unit tests using fake repository contracts.
- [x] Add reusable fake repository implementations in `:core:testing`.
- [x] Add CI wiring for module-boundary checks when CI exists.
- [x] Consider splitting `:core:location` into API and Play-services implementation modules if location code grows beyond the current small adapter surface.
  - Decision: keep the current single `:core:location` module for now because the adapter surface is small and still isolated behind interfaces. Split it into API/implementation modules only if it grows.

## LIMP Architecture Policies

Policy source: `docs/LIMP_POLICIES.md`.

- [x] Research standard Kotlin/Android structure and quality tooling before adding project-specific checks.
- [x] Prefer ktlint, Detekt, and Android lint for general Kotlin/Android quality rules.
- [x] Keep custom LIMP checks broad and small instead of copying Field Guide's custom Dart lint package.
- [x] Add explicit Kotlin editor configuration for ktlint.
- [x] Tighten Detekt complexity, method-length, class-length, nesting, and line-length budgets.
- [x] Add hard file length budgets for production, feature route, and test Kotlin files.
- [x] Add hard import-count budgets for production, feature route, and test Kotlin files.
- [x] Enforce Kotlin package-directory alignment under source roots.
- [x] Enforce that `:core:common` stays plain Kotlin/JVM.
- [x] Enforce that feature production code does not depend on persistence implementation modules.
- [x] Enforce that core modules do not depend on feature modules.
- [x] Enforce that `:core:testing` is not used as a production dependency.
- [x] Move `:core:common` from `kotlinx-coroutines-android` to `kotlinx-coroutines-core`.
- [x] Wire LIMP policies into the local pre-commit gate.
- [x] Wire LIMP policies into GitHub Actions.
- [x] Keep the old module-boundary script as a compatibility wrapper around the LIMP gate.

## Remaining Implementation + Verification

- [x] Implement staged Android 11+ background-location permission UX: foreground first, educational UI, then app-settings handoff for "Allow all the time".
- [x] Add runtime checks that explain approximate-location limitations before registering a precise home geofence.
- [x] Add migration test harness before the first schema upgrade beyond version 1.
- [x] Add CI workflow for module-boundary checks and the full Gradle verification gate.
- [x] Add user-facing delete-all-local-data flow before production release.
- [x] Decide whether the map-pin adjustment must become an actual map UI instead of coordinate/radius fields.
  - Decision: keep coordinate/radius fields for MVP to avoid adding Maps API key handling before the product needs it.
- [x] Decide whether CSV export is post-MVP or near-term.
  - Decision: post-MVP unless the user asks for export before release.
- [!] Run and record the manual device checklist for geofence exit, enter, dwell, delayed delivery, reboot/app restart, and activity transitions.
  - Blocked until a physical Android device with Google Play services is used.
- [!] Add accessibility pass for Compose screens before release.
  - Needs TalkBack/font-scale/input-mode review on device before a release track.
- [!] Add release build/signing configuration when a release track is planned.
  - Needs release keystore/signing direction.
- [!] Record the Play background-location declaration video and review final policy copy before Play release.
  - Needs a physical device and final release UI.
- [!] If automatic mileage is requested later, design it explicitly for privacy and battery before implementation; do not infer miles from stored routes or continuous GPS by default.
