# Plain JSON backups and S25 migration preparation — 2026-08-30

The user requested updating the live S25 without losing its data, preparing that data for the S24, and removing the encryption/password requirement. This supersedes the encrypted-backup UX in the earlier Settings specification.

## App changes

- New backups are readable UTF-8 JSON (`time-tracker-backup.json`) using the Android document picker. Export does not accept or request a password.
- All eight tracking tables are exported in a single database transaction, together with the existing five app preferences. Manifest/schema versions and field names are preserved.
- Restore accepts plain JSON without a password. Older encrypted files remain supported through an optional legacy restore control; this is not required for new backups.
- Existing nonempty-destination protection and transactional rollback remain in place. Restore does not merge with or overwrite existing tracking records.
- No database-schema, tracking-calculation, weekly-table, or Work Hub synchronization-contract change was made for this backup-format follow-up.

## Checks

Release compilation, Spotless, Detekt, release Android lint, and 58 focused unit tests passed: core/sync 19, Settings 11, database 22, app 6. Backup tests verify plaintext restore, identity/settings preservation, rejection of nonempty destinations, legacy encrypted restore/wrong-password rejection, and full rollback when a later row is invalid.

The final release APK has SHA-256 `889a3e1378fcba8c254e883c79efb989baf3c86c085bed4288633b679d487724`. The source-tree fingerprint is `70e9184845fcc48e664c8f35b09b4a2eedc87a712eb470917f5a3b029ca5d5b3`, based on HEAD `c89b24b05e67584da0b2bbece8701edd5c2eb3fa` plus the existing uncommitted UI cleanup and subsequent changes. The original production Maps configuration was supplied to the release build without exposing its value in console output.

## S25 source device

Device: SM-S938U, serial R5CY12JTTPX. The live app is the non-debuggable production package `com.robertochavez.timetracker`, version 0.2.5/code 7. Its original database schema is version 12, established from the installed APK.

The original release lacked backup/export. Android denied direct private-data access, so no raw database copy could be obtained before updating. The original APK and on-screen historical totals were preserved. An authorized in-place release update used the identical signing certificate (SHA-256 `f5cf126445fa8d284bd6138b92d473265630bcf9420232f25eb74728b3abcf22`). Only the existing, tested 12-to-13 additive migration applied: nullable project fields, their index, and an empty Work Hub queue table. No uninstall, clear-data command, destructive fallback, or source-device restore occurred.

The app's original data-directory inode remained unchanged. Two completed weekly tables matched all 72 cell values before and after the update. The backup contains 104 away sessions, 5,407 activity intervals, 110 worksite visits, one home, two workplaces, seven schedule rows, and one presence row; there is no explicitly saved pay-period row. All five preferences are included.

After the password-free update, a native JSON export matched all historical rows, locations, schedule, and preferences from the first data copy. Only the live presence timestamp refreshed on application startup. A local restore into the current SQLite schema passed integrity/reference checks and matched all exported rows. No open away session or worksite visit was present at export time.

The new export and restore screens were visually inspected on the S25; new exports have no passphrase field. The complete weekly table remains visible without scrolling. The production app remains installed and open with its data. Plain backups are also retained in the S25 Downloads folder.

App-UID logs show no fatal exception, ANR, or SQLite exception. Existing device-platform messages include ashmem, Adreno/performance hints, vibrator metadata, and libpenguin. A first-launch SurfaceSyncGroup transaction timeout was also recorded; there was no observed navigation or export failure. This is not a claim that every platform log line was error-free.

## Private backup artifacts and completed transfer

User data is kept outside the repository in `C:/Users/rseba/TimeTrackerBackups/S25-2026-08-30-3dabab5e`, with access restricted to the current Windows user. The requested backup itself is not encrypted and requires no password. The temporary encrypted intermediate and its generated passphrase were removed after the plain copy was verified.

The `Ready-for-S24` directory contains `TimeTracker.apk` and `TimeTracker-backup.json`. The JSON SHA-256 is `3adef45ed6e99bff9ff5446bc43aaee60bb9794050987205bfc100efc8a296c6`. The private folder also retains candidate hashes, build logs, original APK, before/after screenshots, row comparisons, and restore evidence.

The S24 (SM-S921U, RFCX204F7KV) was connected and inspected. Only the earlier `.uiqa` preview existed; it was left untouched. Installed the production release into a new, empty `com.robertochavez.timetracker` installation and completed the normal Android permission flow. Restored the plain backup through the native document picker without a password.

A fresh native export from the S24 matched all 5,632 source rows by ID and value, and all five app preferences. Only the device-local `work_presence.updatedAtEpochMillis` field was excluded from equality because it can refresh on registration; no historical field was excluded. A completed weekly table also matched all 72 S25 cell values. The final S24 screen shows the complete current week without scrolling.

Restart verification confirmed precise/background location, physical activity and notification permissions, the America/New_York timezone, and successful registration of the home geofence, both work locations, and activity detection. Production UID 10413 was used for diagnostics (not the retained preview's UID 10412). The app reported `homeRegistered=true`, `workRegisteredCount=2`, and `activityRegistered=true`, with no fatal exception, ANR, or SQLite exception. The production app remains open on the S24, ready to use.

S24 evidence is in `S24-transfer-verification.json`, `s24-runtime-verification.json`, `S24-restored-verification.json`, `s24-restart-logcat.txt`, and `s24-screens/`. The source and destination apps and plain backup files are intentionally retained. No app was uninstalled or cleared, no new background helper or emulator remains running, and the original S25 data was not removed.

No live Work Hub/Notion activation, work-laptop provider gate, or external synchronization occurred. Verification used the user's requested S25; no new S21 run or emulator was launched.
