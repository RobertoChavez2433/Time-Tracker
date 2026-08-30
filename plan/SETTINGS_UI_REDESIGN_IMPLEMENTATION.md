# Settings redesign — 2026-08-30

Implemented the approved compact Settings proposal in `C:/Users/rseba/Projects/Time_Tracker_Work_Hub`, branch `codex/work-hub-integration`. This follows the original UI cleanup and replaces its initial Settings grouping. The changes remain uncommitted.

## Delivered behavior

- Six compact rows in two groups replace the large navigation cards: Tracking & permissions, Work schedule, Notifications, Work Hub, Backup & transfer, and Privacy & local data. Rows show saved values and actual permission/readiness states.
- Focused pages use compact headers, clear back navigation, consistent rows, and appropriate action sizes. Settings retains the app's existing olive palette.
- Work schedule has seven day selectors, weekly/every-two-weeks choices, a calendar date picker, and a readable current-period preview. Day changes save immediately and are serialized to avoid losing concurrent updates. Pay-period edits have Save, Discard, and unsaved-back confirmation.
- Tracking shows Android permission state and refreshes after returning from Android settings. Activity detection reflects saved state, prevents duplicate requests, and preserves the previous value if disabling fails.
- Work Hub separates everyday status from setup, provider verification, recovery, and fallback credentials. Sync readiness requires the existing activation-date and transport/provider prerequisites. Advanced controls do not imply that a provider gate passed.
- Backup creation and restoration have separate pages, masked passphrase entry, native document pickers, progress protection, and visible results. Passphrases and fallback tokens remain in ViewModel memory rather than Android saved-instance state. Existing encryption and empty-destination restore guards are unchanged.
- Local deletion remains behind an explicit confirmation. Privacy controls remain separate from backup and connection setup.

No weekly-table, report-calculation, tracking-classification, database-schema, radius-limit, or external synchronization-contract changes were made for this Settings follow-up. All `feature/reports` file hashes match the pre-Settings candidate.

## Build and focused checks

Passed compilation, formatting, Detekt, all-module Android lint, and 34 focused unit tests with zero failures, errors, or skips: Settings 11, core/sync 17, app 6. Five new Settings tests cover sync prerequisites, permission readiness, concurrent day changes, a failed activity-disable request, and discarding unsaved pay-period edits.

```powershell
./gradlew.bat '-PtimeTracker.debugApplicationIdSuffix=.uiqa' :app:assembleDebug :feature:settings:testDebugUnitTest :core:sync:testDebugUnitTest :app:testDebugUnitTest spotlessCheck detekt lintDebug --continue --console=plain
```

`git diff --check` also passed. Build output is preserved as `checks.log` in the evidence directory below.

## Physical S21 verification

Result: **passed for the Settings/UI and local backup scope below**.

Device: S21 SM-G996U, serial `RFCNC0Y975L`, Android 15/API 35, 1080x2400, density 450. Used the separate debug preview package `com.robertochavez.timetracker.uiqa`; existing release and `.debug` installations were not replaced or used for testing.

Verified on the final candidate:

- All six Settings categories fit on the initial screen at normal and 140% text size. At 200%, Settings pages reflow and scroll where needed; the schedule day buttons wrap without clipping. Calendar actions remain reachable. Screenshots were visually inspected, not merely captured.
- Changed workdays, cadence, and period start; saved; restarted the app; confirmed updated summaries persisted. Exercised Keep editing and Discard. Restored Monday–Friday, every two weeks, anchor August 24, 2026.
- Toggled activity detection and notification preferences and restored their original effective values.
- Changed background-location permission in Android and returned to the app. The existing startup setup gate correctly intercepted missing background permission. Restored permission through that gate and confirmed Settings refreshed to Allowed. The new missing-background row was not independently observed because the existing gate takes precedence.
- Opened Work Hub overview, setup, advanced status, and checks. Verified the folder check is disabled without an outbox, set/cleared the first sync date, and canceled the native folder picker without configuring an outbox. Provider state remained `NOT_RUN`; no external synchronization occurred.
- Created an encrypted backup using the Android document picker, independently decrypted the 800-byte artifact, and verified its manifest, all eight exported tables, and five app settings. Restored it through the native picker into the preview app, then compared every exported table with the resulting SQLite database. SQLite integrity passed.
- Opened and canceled the local-data deletion confirmation. No locations or sessions were deleted. The preview contained no user locations or sessions before or after these tests.
- Confirmed This Week still shows all 72 cells: all eight columns, seven days, and totals, entirely above bottom navigation, without table scrolling. The normal-font screenshot is `s21/34-this-week.png`.

Saved state has the original effective preference values. Notification false values, the seven default schedule rows, and the default pay-period row are now explicitly persisted after exercising Save/restore; the raw database/preferences bytes therefore differ from the initial default-only state. The temporary Downloads backup was removed, the original font scale of 1.0 restored, and the preview was left installed and open to Settings as requested (`s21/36-open-for-user.png`). No owned emulator, server, or port binding was created for this follow-up.

App-UID diagnostics contain no fatal exception, ANR, SQLite exception, or new app error signature. The existing `libpenguin.so` startup message also appears in the earlier pre-Settings S21 logs and is recorded as baseline noise, not silently treated as an empty error log.

## Evidence and exact candidate

Evidence directory: `C:/Users/rseba/Projects/Time_Tracker_Work_Hub/tools/verification/results/2026-08-30/settings-7e358386cd` (local ignored artifacts).

- HEAD/base: `c89b24b05e67584da0b2bbece8701edd5c2eb3fa` with the existing uncommitted UI cleanup plus this Settings follow-up.
- Mode/build flag: debug, `-PtimeTracker.debugApplicationIdSuffix=.uiqa`.
- Source-tree SHA-256: `eceb13b1205e536395c6e4e574a2cdbe90590e968d1e8653df49780665c87f89`.
- APK SHA-256: `8c4b54b552961bd784ed781b5af7ec00a62e012d31a9e67dba9c22e2126a044b`.
- `candidate.json` records source hashes and the 16 paths changed from the previous preview. `final-identity.json` confirms the source and APK identity remained unchanged.
- `s21/01-*` through `s21/36-*` contain screenshots and UI hierarchies; `preview-before.tar`, `preview-after.tar`, `db-before`, and `db-after` preserve local state evidence.
- `verification-checks.json`, `verification-result.json`, `checks.log`, `logcat.txt`, `export.ttbackup`, and `export-decoded.json` contain assertions, diagnostics, and backup proof.

Excluded: live Work Hub/Notion export, work-phone migration, the full OneDrive/work-laptop provider gate, populated-history migration, map tiles, and cross-device sync. Their existing backend contracts and previous verification limits remain unchanged. No production credentials or external records were used.

## Subsequent backup-format clarification

The user subsequently rejected mandatory backup encryption during the S25-to-S24 migration. New backups now use plain JSON without a passphrase, and restore accepts that format. The prior encrypted behavior above is historical evidence, not the current default. See [Plain JSON backup implementation](PLAIN_JSON_BACKUP_IMPLEMENTATION.md) for the updated format, S25 data-preservation evidence, and migration status.
