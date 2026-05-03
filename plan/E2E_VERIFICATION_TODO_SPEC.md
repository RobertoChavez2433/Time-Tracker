# Time Tracker Premium Olive UI + E2E Verification TODO Spec

## Summary

- [ ] Save this TODO-style spec as `plan/E2E_VERIFICATION_TODO_SPEC.md` as the first execution step.
- [ ] Link the saved spec from `plan/TODO.md`.
- [ ] Preserve all previous E2E verification requirements.
- [ ] Correct the design direction: Field Guide is only a reference for design-system structure, workflow discipline, and E2E verification style.
- [ ] Do not copy Field Guide's visual feel, dense layout, cyan-heavy palette, or operational dashboard tone.
- [ ] Redesign Time Tracker as a simple, premium-feeling dark app with an olive green visual identity.
- [ ] Keep the design system modularized, tokenized, and owned by `:core:designsystem`.
- [ ] Keep all verification/debug behavior behind `-PtimeTracker.e2eDebug=true`.

## Premium Olive Design Direction

- [ ] Replace "Field Guide-inspired dark theme" language with "premium dark olive Time Tracker design system using Field Guide-style modular structure."
- [ ] Preserve the user's dark theme preference while removing cyan as the primary visual identity.
- [ ] Keep Field Guide-inspired structure only:
  - [ ] durable planning docs
  - [ ] reusable design-system module
  - [ ] shared UI primitives
  - [ ] stable verification tags
  - [ ] structured E2E evidence
- [ ] Remove Field Guide visual copying:
  - [ ] cyan primary actions
  - [ ] heavy dark dashboard feel
  - [ ] dense repeated card layout
  - [ ] overly operational copy and screen density
- [ ] Redesign `TimeTrackerColors` around a premium dark olive palette.
- [ ] Use olive green as the primary brand/action color.
- [ ] Add warm dark neutral surfaces and accessible text colors.
- [ ] Add muted sage/stone/gold accents where useful.
- [ ] Preserve accessible contrast.
- [ ] Add tokenized design primitives:
  - [ ] spacing scale
  - [ ] radius scale
  - [ ] elevation policy
  - [ ] border/divider colors
  - [ ] motion durations
  - [ ] easing curves
  - [ ] screen/content density defaults

## Shared Component Cleanup

- [ ] Keep `:core:designsystem` as the UI boundary.
- [ ] Update shared components to feel lighter, simpler, and more premium.
- [ ] Replace dense card walls with grouped panels, rows, and progressive detail.
- [ ] Add button variants:
  - [ ] primary
  - [ ] secondary
  - [ ] quiet
  - [ ] destructive
- [ ] Add cleaner settings rows and grouped settings sections.
- [ ] Add premium motion/transitions:
  - [ ] screen transitions
  - [ ] expanded/collapsed sections
  - [ ] button feedback
  - [ ] status message appearance
  - [ ] confirmation dialogs
- [ ] Preserve or deliberately update stable Compose test tags when UI is redesigned.

## Screen Simplification

- [ ] Home should feel like guided setup/status, not a dense form wall.
- [ ] Tracking should foreground the current tracking state.
- [ ] Manual correction should be secondary/progressive, not visually equal to primary tracking.
- [ ] Reports should show clear headline totals first, then detail.
- [ ] Settings should be reorganized into simpler grouped sections.
- [ ] Reduce long explanatory copy.
- [ ] Clean up navigation/menu weight while preserving stable test tags.
- [ ] Verify all primary app buttons/toggles update state correctly:
  - [ ] bottom navigation destinations
  - [ ] settings permission/setup controls
  - [ ] notification toggles
  - [ ] workday controls
  - [ ] pay-period anchor controls
  - [ ] home/work location buttons
  - [ ] tracking start/stop/edit controls
  - [ ] reports navigation/cards
  - [ ] delete local data flow

## Destructive Reset UX

- [ ] Add delete-data confirmation UI before destructive reset.
- [ ] Confirmation dialog appears after tapping delete local data.
- [ ] Cancel preserves state.
- [ ] Confirm clears expected local data.
- [ ] E2E asserts reset state afterward.
- [ ] Use destructive button styling only for the confirmed destructive action.

## E2E Harness Changes

- [ ] Refactor `tools/testing/Invoke-E2EVerification.ps1` from a mostly linear script into named subflows:
  - [ ] `bootstrap_reset`
  - [ ] `settings_tracking_setup`
  - [ ] `settings_timesheet_rules`
  - [ ] `home_location_controls`
  - [ ] `tracking_session_correction`
  - [ ] `reports_navigation_and_totals`
  - [ ] `bottom_nav_switching`
  - [ ] `persistence_relaunch`
  - [ ] `destructive_reset_confirmation`
  - [ ] `debug_log_audit`
- [ ] Add a flow/matrix artifact that records every verified control:
  - [ ] test tag
  - [ ] screen/subflow
  - [ ] action performed
  - [ ] expected state delta
  - [ ] persistence expectation
  - [ ] pass/fail result
  - [ ] artifact paths
- [ ] Produce canonical artifacts under `tools/testing/test-results/<date>/<run-id>/`:
  - [ ] `summary.json`
  - [ ] `report.md`
  - [ ] `button-state-matrix.json`
  - [ ] state snapshots before/after each major flow
  - [ ] screenshots/UI dumps on failures and key checkpoints
- [ ] Match Field Guide verification style without copying app design:
  - [ ] assert screen sentinels before actions
  - [ ] use stable tags instead of visual text where possible
  - [ ] dismiss keyboard/overlays before taps after text entry
  - [ ] use debug state/log endpoints as truth
  - [ ] keep screenshots as supporting evidence, not primary assertions
- [ ] Add `report.md` visual checklist:
  - [ ] olive primary color is visible
  - [ ] cyan Field Guide primary styling is gone
  - [ ] dark theme is preserved with olive identity
  - [ ] settings/menu density is reduced
  - [ ] destructive reset requires confirmation
  - [ ] motion does not make E2E flaky

## Persistence And State Coverage

- [ ] Add relaunch persistence verification:
  - [ ] complete a real user flow that creates settings, locations, and session/report data
  - [ ] capture `persisted-baseline-state.json`
  - [ ] force-stop the app
  - [ ] relaunch the app
  - [ ] capture `after-relaunch-state.json`
  - [ ] assert persisted fields match
- [ ] Persist/assert these fields at minimum:
  - [ ] home/work configured state and radii
  - [ ] privacy disclosure acceptance
  - [ ] notification preferences
  - [ ] pay-period anchor date
  - [ ] selected workdays / trackable today state
  - [ ] session count
  - [ ] latest session correction data
  - [ ] total driven miles
  - [ ] report totals/cards
- [ ] Add debug-only location simulation if needed for reliable automated location-state checks.
- [ ] Prefer a gated testing endpoint or fake provider input.
- [ ] Avoid relying on Android global mock-location settings for the default E2E path.

## Documentation Tasks

- [ ] Save this spec as `plan/E2E_VERIFICATION_TODO_SPEC.md`.
- [ ] Link it from `plan/TODO.md`.
- [ ] Preserve the original E2E verification TODOs in the saved spec.
- [ ] Append this premium dark olive UI correction without deleting prior E2E context.
- [ ] Update `plan/TODO.md` and `plan/PROGRESS.md` wording so the repo records:
  - [ ] Field Guide structure adapted
  - [ ] Field Guide visual feel rejected
  - [ ] premium dark olive Time Tracker design direction accepted
  - [ ] E2E verification remains required

## Verification Commands

- [ ] Run unit tests:

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
```

- [ ] Run full local quality gate:

```powershell
.\gradlew.bat "-PtimeTracker.e2eDebug=true" spotlessCheck detekt testDebugUnitTest lintDebug assembleDebug --console=plain
```

- [ ] Run full device E2E verification:

```powershell
.\tools\testing\Invoke-E2EVerification.ps1 -DeviceId <device-id> -PackageName com.robertochavez.timetracker.debug
```

- [ ] Confirm generated evidence:
  - [ ] `summary.json` reports passed
  - [ ] `report.md` contains flow results, button matrix summary, persistence comparison, premium olive visual checklist, and debug-log audit
  - [ ] no uncaught errors in debug logs
  - [ ] persistence comparison passes after relaunch

## Assumptions

- [ ] Existing E2E verification requirements remain in scope.
- [ ] This premium dark olive correction is additive and must not erase earlier testing context.
- [ ] The saved spec target is `plan/E2E_VERIFICATION_TODO_SPEC.md`.
- [ ] The default E2E path should be reliable on a normal connected Android device without requiring global Android mock-location configuration.
- [ ] Optional deeper system-permission probing can exist behind a separate flag, but the required app E2E should not depend on device-specific system UI behavior.
- [ ] The design system should remain modularized and tokenized.
- [ ] The final app should feel dark, simple, calm, premium, and distinctly Time Tracker.
