# Red/Green Acceptance Record

## Red baseline — 2026-07-27

The red baseline compiled successfully and failed only because the requested
contracts and guards were absent.

| Test | Prohibited or missing behavior proven by the failure |
| --- | --- |
| `WorkLocationTest.allows twenty five mile radius` | Worksite validation still rejected the requested 25-mile upper bound. |
| `PayPeriodSettingsContractTest.pay period exposes cadence and anchor start date` | Pay-period settings exposed only a biweekly anchor. |
| `PayPeriodSettingsContractTest.pay period supports weekly and biweekly cadence` | No cadence type existed. |
| `TimeTrackerMigrationsTest.schema one has ten upgrade migrations` | No 11→12 migration was registered. |
| `TimeTrackerMigrationsTest.database version includes cadence and radius migration` | The database was still version 11. |
| `TrackingRegistrationSynchronizerTest.address geocoder contract supports reverse lookup` | Dropped pins could not request an optional address. |
| `ReportsViewModelTest.reports expose guarded week navigation contract` | Reports had no previous/next-week state contract. |
| `ReportsViewModelTest.week browsing is bounded and uses independent fixed-size queries` (UX refinement) | A browsed week still used the generic “Selected Week” title instead of its date range. |
| `HomeViewModelTest.places exposes one streamlined add flow with stable automation controls` (UX refinement) | Places had separate always-visible worksite actions rather than one saved-list/Home/Add flow. |
| `HomeViewModelTest.starting add place never reuses the existing place draft` (S21-found regression) | The Add flow reused the first saved place's label, coordinates, address, and radius instead of opening a clean draft. |
| `HomeViewModelTest.opening the map centers its draft on current location without saving a place` (2026-07-28 refinement) | Drop a pin still opened at the continental-US fallback instead of preparing a current-location map center. |
| `HomeViewModelTest.map references label current home and every saved worksite without changing the draft` (2026-07-28 refinement) | The picker had no read-only, labeled reference markers for the current location, Home, or saved worksites. |
| `HomeViewModelTest.places exposes one streamlined add flow with stable automation controls` (Home refinement) | The Home card did not expose stable controls for the three requested location methods or a Home map picker. |
| `HomeViewModelTest.opening the home map centers its draft on current location without saving home` | Home had no current-centered map preparation, and opening the picker could not be proven non-persistent. |
| `HomeViewModelTest.home address lookup and map pin remain drafts until explicit save` | Home had no address lookup/map-pin draft contract, including the guard that Cancel restores the persisted Home and radius. |

Commands:

```powershell
.\gradlew.bat :core:common:test --no-daemon --console=plain
.\gradlew.bat :core:database:testDebugUnitTest :core:location:testDebugUnitTest :feature:reports:testDebugUnitTest --continue --no-daemon --console=plain
.\gradlew.bat :feature:home:testDebugUnitTest :feature:reports:testDebugUnitTest --continue --console=plain
.\gradlew.bat :feature:home:testDebugUnitTest --tests '*starting add place never reuses the existing place draft'
.\gradlew.bat :feature:home:testDebugUnitTest --tests '*opening the home map*' --tests '*home address lookup*' --tests '*places exposes*'
```

The earlier compile-only failure caused by missing `assertTrue` imports was
discarded and is not accepted as red evidence.

The S21-found Add-flow regression compiled and failed at
`HomeViewModelTest.kt:290` with `ComparisonFailure`; after `beginAddPlace()`
reset the editor and the Places button invoked it, all 16 Home ViewModel tests
passed.

The current-location centering test compiled and failed at
`HomeViewModelTest.kt:82` because the map center callback returned null. The
labeled-reference test compiled and failed at `HomeViewModelTest.kt:118`
because the map reference list was empty. Both now pass without changing the
persisted worksite collection.

The Home refinement command compiled successfully and reported `3 tests
completed, 3 failed`: the stable-control assertion failed first at
`HomeViewModelTest.kt:46`, the Home map-center callback returned null at line
174, and the missing address/map draft behavior failed at line 211. The
implemented method chooser, Home map preparation, address lookup, map-pin
draft, and Cancel restoration now satisfy those tests.

## Green closeout

All red tests are now regression tests and pass. The complete code-level gate
passed on 2026-07-27:

```powershell
.\gradlew.bat spotlessApply :core:designsystem:detekt :feature:home:detekt check --continue --max-workers=3
```

Result: `BUILD SUCCESSFUL` with 977 actionable tasks. `git diff --check`
passed, no Git-tracked Google API key was found, no paid Places/Geocoding/Routes
client dependency was found, and none of those paid Maps Platform services is
enabled in the Time Tracker cloud project.

Physical-device acceptance was performed by directly driving S21
`RFCNC0Y975L` (SM-G996U, Android 15). The broad E2E PowerShell driver was not
used. Evidence is under `tools/testing/test-results/2026-07-27/manual-s21`.

- Week back/forward bounds, date-range-only past heading, rotation, process
  relaunch, current-card replacement, and disabled current-week forward state
  passed.
- Weekend persistence, the zero-day warning, and rejection of an off-day
  automatic home-exit session passed.
- Weekly/biweekly preview-versus-save behavior, invalid-date rejection,
  persistence, and deterministic boundary fixtures passed.
- Map disclosure decline produced no Maps initialization; acceptance loaded
  restricted-key Google tiles. Online pin, reverse address, offline/manual
  fallback, 1/13/25-mile values, warning text, Cancel, confirm-without-save,
  and explicit save behavior passed.
- The streamlined saved-list/Home/Add flow passed. Multiple sites retained
  distinct IDs; an injected transition for `South site` produced a
  `work_site_sessions` row with that exact work-location ID and label.
- The S21 upgraded an actual version-11 database containing a 50-foot
  `Legacy site`. Version 12 preserved its identity/location, changed only the
  radius to 1,609.344 meters, defaulted cadence to `BIWEEKLY`, and issued the
  replacement geofence request at 1,609.344 meters.
- The release package was non-debuggable and its debug HTTP endpoint was
  unreachable.

## Current-location and labeled-marker closeout — 2026-07-28

The map picker now obtains a precise current location before opening, centers
at local zoom, and displays read-only blue reference dots with visible labels
for `Current location`, `Home`, and every saved worksite. The red pin remains
the only editable draft.

Physical-device acceptance was performed by directly driving S21
`RFCNC0Y975L`; the broad E2E driver was not used. The first marker build
exposed an `IBitmapDescriptorFactory is not initialized` fatal error on the
device. Explicit Maps initialization after disclosure acceptance fixed that
device-only lifecycle defect. The repeated path then showed the local map,
separate Home/current labels at a shared coordinate, no fatal log, and one
persisted worksite both before and after Cancel. Evidence is under
`tools/testing/test-results/2026-07-28`.

## Home location-method closeout — 2026-07-28

Home now uses one `Edit Pin and Radius` entry point with `Use the current
location`, `Drop a pin on the map`, and `Add by address`. The map shares the
current-centered picker and labeled blue references, while retaining the
pre-existing discrete Home radius choices instead of applying the worksite
1–25-mile slider.

The focused red tests are green. Physical-device acceptance was performed by
directly driving S21 `RFCNC0Y975L`; the broad E2E driver was not used.

- The method chooser exposed all three controls without clipping.
- A public address resolved into draft coordinates and a readable address;
  the persisted Home coordinates and radius remained identical across lookup
  and Cancel.
- The Home map opened at the live device area, showed a red Home draft plus
  labeled blue `Home` and `Current location` references, and had no fatal log.
- Changing the Home radius, moving the pin, and pressing `Use This Pin`
  changed only the review draft. Cancel preserved the original saved Home and
  radius.
- Android system Back dismissed the Home map, returned to the Home method
  editor, and left the persisted Home unchanged.
- `Use the current location` displayed the existing-Home overwrite
  confirmation. Cancelling it preserved the saved Home.
- `Save Home` also required overwrite confirmation for the existing Home.
  Confirming a no-change draft preserved its coordinates/radius, re-registered
  the 15.24-meter Home geofence, and produced no fatal log.

S21 screenshots, UI hierarchies, and sanitized log evidence are under
`tools/testing/test-results/2026-07-28/home-*`.
