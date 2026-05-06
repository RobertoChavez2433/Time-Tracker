# Dashboard Ledger Implementation

## Task List

- [x] Add dashboard weekly ledger UI models with Home, Work, Drive, On-site, and rounded whole miles.
- [x] Replace the current report cards with a compact dashboard summary and weekly ledger.
- [x] Make the ledger the primary Dashboard tab.
- [x] Simplify the old Home setup flow into a clearer Places setup screen.
- [x] Hide manual coordinate fields until the user explicitly chooses manual editing.
- [x] Keep current-location save actions separate from manual pin save actions.
- [x] Update the device verification script for Dashboard and Places navigation.
- [x] Update tests for rounded miles and weekly ledger rows.
- [x] Make the weekly ledger fit in the dashboard width using compact labels and existing spacing tokens.
- [x] Run formatting, tests, and build verification.

## Target Dashboard

```text
Dashboard

Today              Week              Pay Period
Home  9h          Home  38h          Home  76h
Work  7h          Work  29h          Work  58h
Miles 42          Miles 183         Miles 361


This Week
May 4 - May 10

Day   Home   Work   Drive  Site   Miles
Mon   9h15   7h20   2h10   5h10   42
Tue   8h40   6h55   1h35   5h20   31
Wed   9h05   7h10   2h     5h10   39
Thu   -      -      -      -      -
Fri   -      -      -      -      -
Sat   Off    -      -      -      -
Sun   Off    -      -      -      -

Total 27h    21h25  5h45   15h40  112
```

## Terminology

- Home: time inside the home geofence during days that have tracking data.
- Work: time outside the home geofence.
- Drive: driving time.
- On-site: idle/job-site time.
- Drive and On-site are sub-buckets of Work, not the full Work total.
- Miles: rounded whole miles.
- Unclassified: hidden from the normal dashboard.
