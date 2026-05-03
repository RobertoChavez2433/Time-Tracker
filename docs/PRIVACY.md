# Privacy Notes

Time Tracker is local-only by default.

## Data Stored Locally

- Home geofence center and radius.
- Away session start and end times.
- Miles driven per away session when the user enters mileage or odometer values.
- Activity intervals classified as `DRIVE`, `IDLE`, or `UNCLASSIFIED`.
- Work schedule and biweekly pay-period settings.
- Manual correction flags and edited session windows.

## Data Not Stored For MVP

- Routes.
- Continuous GPS traces.
- Speed samples.
- Cloud account data.
- Advertising or analytics identifiers.

## Location Use

Precise location is used to set the user's home point and register a geofence. Background location is required only so Android can deliver home enter, dwell, and exit geofence transitions while the app is not open. The app does not run a continuous GPS tracker.

## Activity Recognition Use

Activity Recognition transitions are used only to classify time within an away session into drive, idle, or unclassified reporting buckets. The primary timesheet total remains away-from-home time.

## In-App Disclosure Text

Automatic tracking uses precise and background location for the home geofence. Activity recognition classifies away time as drive, idle, or unclassified. Data stays on this device unless a future export or sync feature is explicitly added.

## Policy References

- Google Play background location guidance: https://support.google.com/googleplay/android-developer/answer/9799150
- Google Play user data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Android geofencing documentation: https://developer.android.com/develop/sensors-and-location/location/geofencing
- Android Activity Recognition Transition API: https://developer.android.com/develop/sensors-and-location/location/transitions
