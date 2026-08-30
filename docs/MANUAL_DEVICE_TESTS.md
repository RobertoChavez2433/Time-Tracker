# Manual Device Test Checklist

August 30 classification update: Review is retained only in old stored records. Reports include all remaining away time in Idle. See [bucket rules](BUCKET_CLASSIFICATION.md) and [implementation evidence](../plan/REVIEW_TIME_REMOVAL_IMPLEMENTATION.md). Never reset a live S24/S25 installation for verification.

Run these on a physical Android device with Google Play services.

Bucket expectations must follow [BUCKET_CLASSIFICATION.md](BUCKET_CLASSIFICATION.md).

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
- Open Reports and confirm Work, Drive, Site, Idle, and Miles are shown separately where evidence exists.
- Confirm a saved work-site drive counts miles but remains Site time rather than Drive time.

## S21 Bucket Repair Verification

- Use S21 `RFCNC0Y975L` for bucket repair testing and verification.
- Treat the mock-drive script as smoke-only; it is not bucket acceptance evidence because endpoint mode can bypass the full lifecycle.
- Run `tools/testing/Invoke-FullDayBucketVerification.ps1` against the S21 debug app and confirm speed-inferred Drive starts only after 15 mph is sustained for at least 1 minute.
- Optionally run the job-site drive debug scenario as smoke-only plumbing evidence and confirm miles increase, Drive minutes do not increase, and Site minutes increase.
- For a real-drive check, start away from Home, drive for more than 1 minute above city-street speed, stop outside a saved Site, and confirm the report shows Drive for the vehicle segment and Idle for other away time.
- Repeat a short saved-Site drive and confirm the same movement increases Miles while remaining Site time.

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
- While an away session is actively tracking mileage, send an SMS from the device and confirm it sends normally.
- While tracking remains active, receive an SMS and confirm the message and notification arrive normally.
- If the user's normal workflow includes MMS or group texting, receive one while tracking is active and confirm the default texting app behaves normally.
- Open and use the default texting app while tracking is active; confirm Time Tracker never asks to become a keyboard, SMS app, accessibility service, notification listener, or phone/call handler.
