# History, Address Sites, Mileage Startup, and Texting Safety Plan

## Summary

- Keep persistence local-only: continue using the existing Room database and keep Android cloud backup/device transfer disabled.
- Build a deeper history/reporting experience over all saved user data, centered on saved work locations such as `Office` and individual jobsites.
- Add address-based work-site geofences using Android geocoding, saved as normal labeled work locations.
- Fix late mileage/drive tracking by starting vehicle-grade location capture immediately when an away session begins.
- Confirm texting safety with a manifest/code audit and a manual device checklist.

## Key Changes

- Reports/history:
  - Make History a calendar-first view where the selected range displays daily totals by date.
  - Derive each calendar day from persisted Room-backed tracking/session data instead of adding a second totals store that can drift from the source data.
  - Keep the biweekly pay period as a first-class report range driven by `PayPeriodSettings.biweeklyAnchorStartDate`.
  - Extend reports beyond today/week/current biweekly pay period with week, month, year, and custom date range views.
  - Range filters should drive the selected totals and calendar: Week, Pay Period, Month, Year, and Custom.
  - Add site-focused grouping: each saved work location shows total site time, visit count, last visit, miles/drive context where applicable, and daily breakdown rows.
  - Add filters for date range and site label, with the calendar/day totals first and site rows beneath it.
  - Use existing `work_site_sessions` as the source of truth for every visit; add range-aware DAO/repository queries if needed so large histories do not require loading all rows forever.

- Address geofences:
  - Add an "Add by Address" path in the work-site editor.
  - Use Android `Geocoder` behind a small app-owned interface, returning candidate address text plus latitude/longitude.
  - Let the user confirm label, radius, and resolved address before saving.
  - Store the address text as optional metadata on `WorkLocation`, requiring a Room migration from version 10 to 11.
  - Register the saved address site through the existing work geofence registrar.

- Mileage and drive classification:
  - On home geofence exit, start `DriveMileageTracker.startTracking()` in `VEHICLE` mode immediately instead of beginning in slower balanced mode.
  - Clear stale movement baseline at away-session start and seed the first baseline from a fresh accurate current/last location when available.
  - Keep activity recognition transitions as supporting classification, but do not wait for `IN_VEHICLE` before high-accuracy mileage capture begins.
  - Preserve existing privacy behavior: do not persist route geometry, speed samples, or continuous GPS traces.

- Texting interference verification:
  - Document that the app declares no SMS, telephony, accessibility, input method, notification listener, or call-screening permissions/components.
  - Add a manual device test checklist: while tracking is active, send SMS, receive SMS, receive MMS/group text if relevant, open default texting app, and verify notifications still appear.
  - Do not add SMS-related permissions or APIs.

## Public Interfaces / Types

- Add optional address metadata to `WorkLocation` and `WorkLocationEntity`, for example `address: String?`.
- Add report UI models for site history summaries and daily site rows.
- Add report UI models for calendar weeks and calendar day cells, including per-day home, work, drive, and mileage totals.
- Add a small geocoding abstraction, for example `AddressGeocoder`, so address lookup can be unit-tested without Android framework calls.
- Keep backup rules unchanged: `time_tracker.db` remains excluded from cloud backup and device transfer.

## Test Plan

- Unit-test week/pay-period/month/year/custom report aggregation by site label, including multiple visits to the same site and renamed-site label snapshots.
- Unit-test calendar day generation for selected ranges, including daily home/work/drive/mileage totals and week alignment.
- Unit-test address geocoder success, no-result, and failure states through the abstraction.
- Add Room migration test for version 10 to 11 and verify existing work locations receive null/empty address metadata safely.
- Update mileage tests to prove away-session start immediately requests vehicle tracking and seeds/clears baseline correctly.
- Run existing test suite plus targeted report, database, location, and settings tests.
- Perform manual device verification for address geofence save, longer history filters, earlier drive start, and texting send/receive while tracking is active.

## Assumptions

- `Office` is a saved work location named `Office`; jobsites are saved work locations with their own labels.
- Every visit is preserved through `work_site_sessions`; the UI surfaces those visits through summaries and daily breakdowns.
- Address lookup uses Android `Geocoder`; no third-party maps/geocoding SDK or API key is introduced.
- Local-only persistence is intentional: data survives app restarts and normal use, but not uninstall/cloud backup/device transfer.
