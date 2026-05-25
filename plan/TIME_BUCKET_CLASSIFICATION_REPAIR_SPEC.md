# Bucket Classification Repair TODO Spec

## Summary

- [ ] Treat old planning docs as source of truth unless this spec explicitly revises them.
- [ ] Revise the old "do not use GPS speed for drive classification" rule.
- [ ] New rule: use point-to-point speed as supporting evidence for Drive classification, without storing routes or speed history.
- [ ] Keep `Work` as total trackable time away from Home.
- [ ] Replace silent auto-fixes with user review when data is uncertain.

## Required First Steps

- [ ] On S25, uninstall the release app and remove its app data:
  - [ ] `com.robertochavez.timetracker`
- [ ] On S25, uninstall the debug app and remove its app data:
  - [ ] `com.robertochavez.timetracker.debug`
- [ ] Confirm S25 no longer has either Time Tracker package installed.
- [ ] Break the current dirty working tree into logical commits and commit before new repair work:
  - [ ] planning/spec docs
  - [ ] movement evidence/domain model changes
  - [ ] database/schema changes
  - [ ] location/activity tracking changes
  - [ ] reports/UI/test changes
- [ ] Update app documentation before implementing bucket logic:
  - [ ] Create or update a durable bucket/classification source-of-truth doc.
  - [ ] Define Home, Work, Drive, Site, Idle, Needs review, and Miles.
  - [ ] Explicitly record the revised speed-evidence rule.
  - [ ] Explicitly record privacy limits: no route geometry, no speed history, no continuous route log.
  - [ ] Link the bucket doc from `plan/TODO.md`, `docs/PRIVACY.md`, and relevant testing docs.
- [ ] Add a custom lint/policy gate so bucket rules stay locked:
  - [ ] Add a small policy check under `scripts/quality`.
  - [ ] Fail if bucket docs are missing required terms/rules.
  - [ ] Fail if production code reintroduces broad auto-fix language or hidden bucket rewrites without review.
  - [ ] Wire the policy check into the existing LIMP/pre-commit quality gate.
- [ ] Then begin bucket repair implementation.

## Bucket Rules

### Home

- [ ] Time inside Home geofence is Home.
- [ ] Home wins over Drive, Site, Idle, and Needs review.
- [ ] Do not count miles inside Home.

### Work

- [ ] Work is total trackable time away from Home.
- [ ] Work does not require `Drive + Site + Idle` to equal 100 percent when evidence is uncertain.
- [ ] Reports may show `Work`, `Drive`, `Site`, `Idle`, `Needs review`, and `Miles`.

### Drive

- [ ] Drive is away-from-home vehicle time outside saved work-site geofences.
- [ ] Use Android Activity Recognition when available.
- [ ] Use speed-based evidence when Activity Recognition is missing, late, or unreliable.
- [ ] Initial inference threshold: at least `15 mph` sustained for `1 minute`.
- [ ] Do not store route geometry or speed history.

### Site

- [ ] Time inside a saved work-site geofence is Site.
- [ ] Site wins over Drive minutes.
- [ ] Driving inside Site remains Site time.
- [ ] Miles driven inside Site can still count toward daily miles.

### Idle

- [ ] Idle is confirmed away-from-home, outside-site, non-driving time.
- [ ] Do not silently place uncertain time into Idle.

### Needs Review

- [ ] Use when the app cannot confidently classify time.
- [ ] Ask the user where the time belongs before changing buckets.
- [ ] Remove broad auto-fix behavior that writes guessed data into buckets.

## Verification TODO

- [ ] Use S25 only as the reset/evidence source.
- [ ] Use S21 for all testing and verification.
- [ ] Add unit tests for bucket priority.
- [ ] Add speed-based Drive tests for `15 mph for 1 minute`.
- [ ] Add tests proving job-site driving counts miles but not Drive time.
- [ ] Add S21 mock-drive verification.
- [ ] Add S21 real-drive manual verification.
- [ ] Verify SMS safety: no SMS permissions, no SMS role, no SMS receivers/services.
