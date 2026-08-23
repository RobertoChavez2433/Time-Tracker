# Privacy Notes

Time Tracker is local-only by default. Work Hub export is optional, user-configured, and begins only from the chosen activation date.

Bucket and movement privacy rules are defined in [BUCKET_CLASSIFICATION.md](BUCKET_CLASSIFICATION.md).

## Data Stored Locally

- Home geofence center and radius.
- Away session start and end times.
- Miles driven per away session when the user enters mileage or odometer values.
- Activity intervals classified as `HOME`, `DRIVE`, `SITE`, `IDLE`, or `NEEDS_REVIEW`.
- Work schedule and biweekly pay-period settings.
- Manual correction flags and edited session windows.
- Work-site project numbers and immutable project-number snapshots on site sessions.
- Work Hub outbox state, content hashes, transfer IDs, retry counts, and sanitized operational errors.

## Data Not Stored

- Routes.
- Continuous GPS traces.
- Speed samples.
- Cloud account data.
- Advertising or analytics identifiers.

## Optional Work Hub Export

When the user enables Work Hub sync, the app exports only:

- Date-level totals for work-away minutes, drive minutes, site minutes, idle minutes, needs-review minutes, and mileage.
- Project/day site summaries containing the normalized project number, stable local worksite ID, worksite label, first arrival, last departure, onsite minutes, visit count, and provisional/final status.
- Stable source IDs, schema/transfer versions, content hashes, and timestamps required for duplicate-safe delivery and receipts.

The export does **not** contain coordinates, addresses, geofence radii, routes, speed samples, raw location observations, or the home location. Drive and mileage are never assigned to an individual project. Local tracking remains authoritative and never waits for sync.

The preferred transport writes encrypted-at-rest device data summaries as JSON through a user-selected OneDrive for Business document-provider folder. If that Android provider fails the app's create/rename/read feasibility test, the user may explicitly enable direct Notion synchronization for time summaries. The Notion token is encrypted by Android Keystore, excluded from backup, and never logged.

Encrypted phone-transfer backups contain local tracking source data, worksite/session IDs, and project numbers. They exclude Notion tokens, document-tree grants, sync configuration, logs, and credentials. Import is permitted only into an empty app database and is authenticated with AES-GCM using a passphrase-derived key.

## Data Deletion

Settings includes a delete-local-data action that clears saved home location, away sessions, activity intervals, mileage, schedules, pay-period settings, Work Hub sync records/configuration/credentials, notification/privacy preferences, and automation registrations on this device.

## Location Use

Precise location is used to set the user's home point and register a geofence. Background location is required only so Android can deliver home enter, dwell, and exit geofence transitions while the app is not open. The app does not run a continuous GPS tracker.

When the app is already tracking away-from-home time, adjacent location samples may be compared in memory to compute point-to-point speed as supporting Drive evidence. Time Tracker must not persist route geometry, speed history, or a continuous route log.

## Activity Recognition Use

Activity Recognition transitions are used only to classify time within an away session into Drive, Idle, Site, or Needs review reporting buckets. The primary timesheet total remains away-from-home Work time.

## Texting and Device Input Non-Interference

Time Tracker must not request SMS, phone, accessibility, input method, notification listener, or call-screening access. It cannot read texts, send texts, become a keyboard, inspect notification contents, or block the default texting app through the app permissions and components declared by this project.

The manifest privacy guard test fails if future manifests introduce texting or device-input interception capabilities. Manual release checks should still verify SMS send/receive while tracking is active on a physical device.

## In-App Disclosure Text

Automatic tracking uses precise and background location for the home geofence. Activity recognition and privacy-limited movement checks classify away time as Drive, Site, Idle, or Needs review. Data stays on this device unless the user explicitly configures Work Hub summary export or creates an encrypted phone-transfer backup.

## Policy References

- Google Play background location guidance: https://support.google.com/googleplay/android-developer/answer/9799150
- Google Play user data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Android geofencing documentation: https://developer.android.com/develop/sensors-and-location/location/geofencing
- Android Activity Recognition Transition API: https://developer.android.com/develop/sensors-and-location/location/transitions
