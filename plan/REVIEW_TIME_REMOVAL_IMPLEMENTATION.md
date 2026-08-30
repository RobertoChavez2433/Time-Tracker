# Review removal implementation and verification

Date: August 30, 2026. Implements the explicitly approved Idle definition in [BUCKET_CLASSIFICATION.md](../docs/BUCKET_CLASSIFICATION.md). Existing working-tree changes were retained; no production installation, database migration, historical record conversion, or sync activation was performed.

## Result

Reports partition non-overlapping Work ranges into Drive, saved Site, and residual Idle. Home, Site, exclusions, schedules, midnight, and daylight-saving rules retain their priority. Legacy Review intervals and activity gaps now contribute to Idle in both historical and active reports. Display rounding is unchanged; equality is asserted before rounding.

The August 24–30 backup replay produces:

| Metric | Exact duration | Display |
| --- | --- | --- |
| Work | 61h 52m 47.618s | 61h 52m |
| Drive | 25h 17m 48.441s | 25h 17m |
| Site | 0s | 0m |
| Idle | 36h 34m 59.177s | 36h 34m |

Mileage remains 657.1197628423523 miles (display 657). Home calculation is unchanged; today's Home grows as the clock advances. Rounded components need not add to the rounded Work display.

Overview retains Day, Home, Work, Drive, Site, Idle, Miles, with all seven days and Total visible together. Only the header and Total have a different background; today/Sunday is not highlighted. Overview, History, and day detail omit Review and expose the short Idle definition.

## Classification and compatibility

- Activity, GPS, and geofence adapters submit evidence to one serialized coordinator and pure reducer. Priority is Home, saved Site, supported Drive, Idle.
- Walking/running entry and vehicle exit clear older vehicle evidence. STILL exit preserves it. Slow GPS does not cancel active Activity Recognition vehicle evidence. New sustained GPS evidence can restore Drive after an activity conflict.
- GPS still requires 15 mph for one minute. Its supported lead-in can only change the current open interval. Mileage calculation is preserved.
- Sessions atomically start with Idle. Repeated decisions coalesce; stale callbacks cannot delete or overwrite closed newer intervals. Writes verify the expected active session.
- Session-scoped flags and timestamps survive process restart. Session end and successful backup restore clear transient evidence. Diagnostic reasons and timestamps contain no location trail.
- Legacy NEEDS_REVIEW enum/database/backup values remain readable. Report compatibility fields are zero. No new Review classifications or Review summary statuses are generated.
- Existing Work Hub synchronization regenerates eligible daily payloads with the new partition and readable text. Remote compatibility properties remain. No external sync was enabled or executed for this verification.
- A side-by-side debug endpoint port collision discovered during verification was repaired: an occupied port falls back to a free loopback port, and state responses identify the application ID. This endpoint remains gated to E2E-enabled debug builds.

## Automated checks

All 164 unit tests passed with no failures or skips. Spotless, Detekt, Android lint, and debug app/instrumentation builds passed. The private-backup replay is opt-in through TIMETRACKER_REPLAY_BACKUP and was executed, not skipped. Its importer/exporter comparison preserves every table value and setting, including 733 legacy Review rows. No private fixture is checked in.

Regression coverage includes unsaved-site walking, STILL/vehicle exit, conflicting GPS/activity evidence, Home/Site priority, session-start coverage, duplicate/delayed events, backdating, state recreation, new-session isolation, overlapping ranges, exclusions, active midnight, and both daylight-saving transitions.

Build command (PowerShell):

```powershell
$env:TIMETRACKER_REPLAY_BACKUP = '<private plain JSON backup path>'
.\gradlew.bat spotlessApply spotlessCheck :core:common:test testDebugUnitTest detekt :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest '-PtimeTracker.debugApplicationIdSuffix=.uiqa' '-PtimeTracker.e2eDebug=true' --continue --console=plain
```

Classification policy checks pass. The existing LIMP gate still reports line/import limits in DebugStateEndpointServer, DirectNotionHubSyncTransport, WorksiteMapPicker, SettingsComponents, and SettingsViewModel. No hook was bypassed and no commit was created.

## Physical S21 evidence

The isolated package is com.robertochavez.timetracker.uiqa on SM-G996U (RFCNC0Y975L). The original preview databases, settings, and files were archived before testing. Production and the other debug installation were not stopped, updated, or cleared. S24/S25 live data was untouched.

Run artifacts are local and ignored under tools/verification/results/2026-08-30/review-removal-85cde427/. candidate.json binds the final source tree and APK hashes; verification-result.json records the final outcome. Screenshots, UI bounds/color assertions, native JSON comparisons, production-logic instrumentation results, session interval assertions, and package-specific logs accompany it. Earlier candidate screenshots and unverified shared-port endpoint responses are superseded and must not be used as final evidence.

Final result: **passed** for the stated S21 preview contract. APK SHA-256: `77e2592ea7bea82a37b64e656a92ca16e369041578d48c6904cfe1968d29010d`. Candidate source fingerprint: `dfc7c45334c489d95afbf58191d7b8ccdf0bc9581df9a398da7ce916746c4bf6`. Final diagnostics contain zero application errors, crashes, or ANRs. The device's unrelated Samsung graphics loader message about libpenguin.so caused no observed rendering failure. All 13 original preview files were restored byte-for-byte before a clean launch; original empty tracking data and SQLite integrity were reconfirmed afterward. Font scale was restored, the task's instrumentation helper, downloads, UI dump and forwarding were removed, and the preview was stopped. Other installed apps were left running.

Native JSON import/export preserves all 5,632 backup rows and five settings, except the device-local work_presence.updatedAtEpochMillis refresh timestamp. Historical activity values are unchanged. The direct importer/exporter unit round-trip preserves that timestamp as well.

Controlled signals are app-local or explicit preview-only broadcasts. No device-wide mock location provider was installed. Process death/relaunch, vehicle/STILL transitions, saved-site entry/exit, Home reset, and real DriveMileageTracker GPS thresholds were exercised. These checks prove implementation behavior under supplied evidence, not walking/driving field accuracy. A transient USB offline interruption was recovered; assertions were rerun after reconnection.

## Production rollout boundary

Production rollout has not occurred. When requested, first create and verify a **fresh plain JSON backup from the live phone**, recording table counts, settings, checksum, active-session state, and exact build/signing identity. Never rely solely on this older saved-week fixture.

Build the production package without preview/E2E flags, verify its signing certificate matches the installed app, and perform only an in-place update. Do not uninstall, clear app data, or import over a populated live app. Confirm counts, legacy rows, active state, and week totals after installation. Leave sync disabled unless it was already enabled. Complete an observed real walking/driving check before claiming field accuracy.
