# Full-Day Bucket Verification TODO Spec

## Summary

Create an S21 full-day verifier that simulates an entire trackable workday through the real automation chain, then proves every minute and mile lands in the correct bucket. The current `50m` and `20m` location thresholds are meters, not miles or minutes, so they are not the suspected root issue. The verification gap is that prior mocks tested isolated shortcuts instead of the full workday lifecycle.

## Key Changes

- [ ] Save this spec as `plan/FULL_DAY_BUCKET_VERIFICATION_SPEC.md`.
- [ ] Mark endpoint mock-drive verification as smoke-only, not acceptance.
- [ ] Add a new S21 full-day verifier script.
- [ ] Default device: S21 `RFCNC0Y975L`.
- [ ] Default package: `com.robertochavez.timetracker.debug`.
- [ ] Use Android test location provider delivery for simulated movement.
- [ ] Use `/testing/state` only for read-only observation and artifacts.
- [ ] Do not use debug endpoints to manually start/finish away sessions or directly call drive tracking.
- [ ] Verify location thresholds are meters:
  - [ ] Balanced mode: `50m`.
  - [ ] Vehicle mode: `20m`.

## Full-Day Scenario

- [ ] Install debug app with `-PtimeTracker.e2eDebug=true`.
- [ ] Reset app data.
- [ ] Grant location and activity-recognition permissions.
- [ ] Accept privacy disclosure.
- [ ] Enable activity detection.
- [ ] Configure today as a trackable workday.
- [ ] Configure Home geofence.
- [ ] Configure at least one saved work-site geofence.
- [ ] Register Home geofence, Site geofence, activity transitions, and movement tracking through normal app code.
- [ ] Start simulated day inside Home.
- [ ] Verify Home time is Home and no miles count.
- [ ] Simulate Home exit.
- [ ] Verify actual Home geofence EXIT starts the away session.
- [ ] Verify away movement tracking starts in balanced mode.
- [ ] Simulate commute drive outside Home and outside Site.
- [ ] Verify Drive does not start before `15 mph` is sustained for `1 minute`.
- [ ] Verify Drive starts after the sustained threshold.
- [ ] Verify commute miles count.
- [ ] Simulate arrival inside saved Site.
- [ ] Verify Site wins over Drive time.
- [ ] Simulate movement/driving inside Site.
- [ ] Verify Site minutes increase, Drive minutes do not, and miles can still count.
- [ ] Simulate stopped non-driving time at Site.
- [ ] Verify time remains Site.
- [ ] Simulate leaving Site.
- [ ] Verify uncertain away/outside-site gap becomes Needs review, not silent Idle.
- [ ] Simulate confirmed non-driving away time outside Home and outside Site.
- [ ] Verify confirmed Idle lands in Idle.
- [ ] Simulate return commute drive.
- [ ] Verify Drive and miles increase only for away/outside-site drive time.
- [ ] Simulate return inside Home.
- [ ] Verify actual Home geofence ENTER/DWELL stops the away session.
- [ ] Verify remaining time inside Home is Home and no Home miles count.

## Bucket Acceptance

- [ ] Work equals total trackable away-from-Home time.
- [ ] Home wins over Drive, Site, Idle, and Needs review.
- [ ] Site wins over Drive time.
- [ ] Drive is away-from-Home vehicle time outside saved Site geofences.
- [ ] Idle is only confirmed away/from-home, outside-site, non-driving time.
- [ ] Needs review is used for uncertain away/outside-site gaps.
- [ ] Miles do not count inside Home.
- [ ] Miles can count inside Site.
- [ ] Reports show Work, Drive, Site, Idle, Needs review, and Miles separately.
- [ ] Drive + Site + Idle does not have to equal Work when Needs review exists.
- [ ] No route geometry, speed history, or continuous route log is persisted by production app data.

## Activity And Movement Coverage

- [ ] Verify Home/Site geofence events are the session and Site initiators.
- [ ] Verify provider-delivered location broadcasts are received by the app.
- [ ] Verify speed-based Drive inference from point-to-point movement.
- [ ] Verify movement tracking escalates from balanced to vehicle mode after Drive inference.
- [ ] Verify movement tracking de-escalates after driving stops.
- [ ] Verify Android Activity Recognition registration succeeds.
- [ ] Verify ActivityTransition receiver behavior with injected/observable transition payloads where possible.
- [ ] Require a physical S21 real-day or real-drive check for Play Services Activity Recognition detector behavior that cannot be fully forced by ADB location simulation.

## Scenarios

- [ ] `full_day_bucket_lifecycle`: Home, commute Drive, Site, Site miles, Needs review gap, confirmed Idle, return Drive, Home.
- [ ] `below_threshold_no_drive`: movement below `15 mph`, no Drive classification.
- [ ] `short_burst_no_drive`: above `15 mph` for less than `1 minute`, no Drive classification.
- [ ] `site_drive_counts_miles_not_drive`: Site minutes increase, Drive minutes do not, miles increase.
- [ ] `home_movement_excluded`: Home movement does not count miles or Drive time.
- [ ] `non_trackable_day_no_work`: Home exit on a non-workday does not create Work time.
- [ ] `real_s21_activity_workday`: physical S21 workday/drive verifies real Activity Recognition behavior.

## Artifacts And Acceptance

- [ ] Save simulated route inputs, expected bucket timeline, state snapshots, screenshots, recent app logs, and summary report under `tools/testing/test-results/<date>/<run-id>/`.
- [ ] Report each process stage as pass/fail, not just final totals.
- [ ] Include expected-vs-actual minutes and miles per bucket.
- [ ] Fail if any bucket differs outside an explicit tolerance.
- [ ] Fail if Drive minutes appear only after direct mock endpoint injection.
- [ ] Fail if no Home geofence exit is observed.
- [ ] Fail if no Site geofence enter/exit is observed.
- [ ] Fail if no location broadcast is observed.
- [ ] Fail if Drive starts before the sustained threshold.
- [ ] Fail if uncertain time is silently placed into Idle.
- [ ] Fail if miles or Drive time count inside Home.
- [ ] Fail if Site driving counts as Drive minutes.
- [ ] Keep SMS safety verification in the final gate.
