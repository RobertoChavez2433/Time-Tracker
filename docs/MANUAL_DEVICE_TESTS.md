# Manual Device Test Checklist

Run these on a physical Android device with Google Play services.

## Permissions

- Install a debug build.
- Open Settings.
- Read the background location and activity recognition disclosure.
- Request foreground tracking permissions.
- Confirm precise location is granted.
- Enable background location separately.
- On Android 11 and higher, confirm the app opens system settings and the user can choose `Allow all the time`.
- Confirm activity recognition is granted.

## Home Setup

- Open Home.
- Tap `Use Current Location`.
- Confirm a home location summary appears.
- Save a nearby adjusted pin by entering latitude, longitude, and radius.
- Confirm the geofence registration action succeeds.

## Geofence Behavior

- Start inside the home geofence.
- Leave the geofence by more than the configured radius.
- Wait for the Android geofence latency window. On Android 8.0 and higher, background geofence events can arrive every few minutes.
- Confirm an away session starts after exit on a trackable day.
- Return inside the home geofence and remain long enough for enter/dwell.
- Confirm the active away session stops.
- Repeat on a non-workday and confirm exit does not start a session.

## Activity Recognition

- Open Settings and enable activity detection.
- Start or trigger an away session.
- Drive long enough for an `IN_VEHICLE` transition.
- Stop and remain still long enough for a `STILL` transition.
- Open Reports and confirm drive, idle, and unclassified buckets are shown separately.

## Manual Corrections

- Open Tracking.
- Edit a session start or end instant.
- Enter or correct miles driven for the session.
- Save the manual correction.
- Toggle whether the session counts toward totals.
- Confirm Reports update after the edit.
- Open Settings.
- Tap `Delete Local Data`.
- Confirm home, sessions, mileage, reports, work schedule changes, and app preferences are cleared.

## Privacy Regression

- Verify no route list, map trace, speed history, or continuous GPS log is visible or persisted.
- Confirm default notification settings are off.
- Enable optional notification settings and confirm no foreground service behavior is introduced without a deliberate future implementation.
