# Release And Policy Readiness

## Current Release Position

This project is not ready for a Play Store production track until physical-device testing and policy copy review are complete.

## Background Location Justification

The core feature is automatic timesheet tracking based on leaving and returning home. Android geofence transitions can occur while the app is not visible, so background location is needed for home exit, enter, and dwell events. The app does not store routes, speed, or continuous location updates.

## Google Play Background Location Requirements

Before a Play release that requests `ACCESS_BACKGROUND_LOCATION`, prepare:

- A Permissions Declaration Form in Play Console.
- A short device video showing:
  - The automatic tracking feature.
  - The prominent in-app disclosure.
  - The runtime permission prompt.
- A privacy policy available in-app and on the store listing.
- Store listing copy that describes automatic location-based timesheet tracking as core functionality.

Google's policy requires background location to provide significant user benefit and be relevant to the app's core functionality. It also requires prominent disclosure that includes the term `location`, describes background use, and lists the features using background location.

Reference: https://support.google.com/googleplay/android-developer/answer/9799150

## Data Safety Notes

Expected Data safety answers for MVP:

- Location: collected locally for app functionality.
- Activity recognition: used locally for app functionality.
- Data sharing: none for MVP.
- Data deletion: user should be able to delete local sessions/settings before production release.

Reference: https://support.google.com/googleplay/android-developer/answer/10144311

## Pre-Release Blockers

- Add a user-facing delete-all-local-data flow.
- Record the background location declaration video.
- Review disclosure text against the exact release UI.
- Run the full manual device checklist.
- Confirm target SDK policy requirements at the time of release.
