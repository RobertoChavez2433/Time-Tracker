# Privacy Notes

Time Tracker is local-only by default.

Bucket and movement privacy rules are defined in [BUCKET_CLASSIFICATION.md](BUCKET_CLASSIFICATION.md).

## Data Stored Locally

- Home geofence center and radius.
- Away session start and end times.
- Miles driven per away session when the user enters mileage or odometer values.
- Activity intervals classified as `HOME`, `DRIVE`, `SITE`, `IDLE`, or `NEEDS_REVIEW`.
- Work schedule and biweekly pay-period settings.
- Manual correction flags and edited session windows.

## Data Not Stored For MVP

- Routes.
- Continuous GPS traces.
- Speed samples.
- Cloud account data.
- Advertising or analytics identifiers.

## Data Deletion

Settings includes a delete-local-data action that clears saved home location, away sessions, activity intervals, mileage, schedules, pay-period settings, notification/privacy preferences, and automation registrations on this device.

## Location Use

Precise location is used to set the user's home point and register a geofence. Background location is required only so Android can deliver home enter, dwell, and exit geofence transitions while the app is not open. The app does not run a continuous GPS tracker.

When the app is already tracking away-from-home time, adjacent location samples may be compared in memory to compute point-to-point speed as supporting Drive evidence. Time Tracker must not persist route geometry, speed history, or a continuous route log.

## Activity Recognition Use

Activity Recognition transitions are used only to classify time within an away session into Drive, Idle, Site, or Needs review reporting buckets. The primary timesheet total remains away-from-home Work time.

## Texting and Device Input Non-Interference

Time Tracker must not request SMS, phone, accessibility, input method, notification listener, or call-screening access. It cannot read texts, send texts, become a keyboard, inspect notification contents, or block the default texting app through the app permissions and components declared by this project.

The manifest privacy guard test fails if future manifests introduce texting or device-input interception capabilities. Manual release checks should still verify SMS send/receive while tracking is active on a physical device.

## In-App Disclosure Text

Automatic tracking uses precise and background location for the home geofence. Activity recognition and privacy-limited movement checks classify away time as Drive, Site, Idle, or Needs review. Data stays on this device unless a future export or sync feature is explicitly added.

## Policy References

- Google Play background location guidance: https://support.google.com/googleplay/android-developer/answer/9799150
- Google Play user data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Android geofencing documentation: https://developer.android.com/develop/sensors-and-location/location/geofencing
- Android Activity Recognition Transition API: https://developer.android.com/develop/sensors-and-location/location/transitions
