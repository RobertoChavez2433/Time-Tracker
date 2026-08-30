# Time Tracker UI Cleanup and Saved-Place Editing

## 1. User requirements

- Keep the existing **This Week table**, including every column, its order, and the information displayed. Improve its appearance—not its structure.
- Keep the existing colors. Fix spacing, visual clutter, duplication, and confusing workflows.
- Make This Week the main screen’s primary content.
- Show the worksites visited during that week and their onsite time directly below the table.
- Remove the endless session-card list from Tracking. Preserve all history and make it accessible by date.
- Make saved places directly editable: name, job number, location, and radius.
- Retain current radius limits: whole miles from 1–25 for worksites and existing home-radius presets.
- Show a compact tracking-status line; put manual Start/Stop controls behind its menu.

## 2. Screen organization

### Overview

Replace the separate Dashboard and Tracking destinations with a single Overview destination:

1. Compact live tracking status and manual-controls menu.
2. **This Week**, retaining the existing table and previous/next-week navigation.
3. **Worksites This Week**, following the same selected week.

Improve table padding, column alignment, header contrast, row separators, and total-row emphasis. Keep it a table at every screen width. Where necessary, scroll only the table horizontally rather than clipping values or shrinking text.

Remove duplicate summary panels, embedded historical calendars, repeated day cards, and configuration controls from this screen.

Worksite rows show name, job number, and onsite total. Selecting one opens its dates and visits for the selected week. Drive time and mileage remain overall day/week information, never per-worksite allocations.

### History

Create a dedicated History destination using the existing calendar and reporting capabilities.

- Default to the current month; retain week, pay-period, month, year, and custom-range selection in a compact filter control.
- Show one navigable month of calendar cells at a time, even for longer selected ranges.
- Selecting a date opens its totals, worksite visits, and recorded sessions.
- Session corrections open from that day’s session rows, not an all-history feed.
- Selecting a day in the weekly table opens this same day-detail screen.

### Places

Use a compact saved-place list with an obvious Edit action and a prominent Add Place button.

Selecting a place opens a dedicated full-screen editor—not an editor appended below the list. Show **Edit Place** or **Add Place** correctly.

The editor contains name, job number, address/location, radius, and Save/Cancel. Map, address lookup, and current-location actions update the draft; none silently saves it. Raw coordinates stay behind an advanced control.

Home uses the same editing conventions but does not require a job number.

### Settings

Replace the long wall of controls with grouped settings destinations: Tracking Setup, Notifications, Work Hub, Backup & Transfer, and Privacy & Local Data. Preserve existing functionality and safety gates.

## 3. Engineering changes

- Reuse the existing reporting calculations, range-based repository queries, project-number storage, and stable worksite IDs. No database migration is required for this UI work.
- Separate overview, history, selected-day, and place-editor state so navigation does not duplicate entire reporting or editing implementations.
- Add route arguments for selected date and worksite ID. Preserve selected week, history filters, and editor drafts across navigation and configuration changes.
- Build weekly worksite summaries from the existing midnight-safe session splitter, grouped by stable worksite ID—not display name. Include legacy sites without job numbers and label them as unassigned.
- Preserve historical name/project snapshots when editing a saved place. Apply existing project-number normalization, uniqueness, and initial null-snapshot backfill rules.
- Introduce explicit editor loading, saving, validation, success, and failure states. Disable duplicate saves; close only after persistence succeeds. Distinguish a saved place with a geofence-registration warning from a failed save.
- Cancel discards the draft without modifying the saved place. Back navigation prompts before discarding unsaved changes.
- Reuse one session-correction editor with visible validation and atomic saving of its fields.
- Consolidate shared spacing, section headers, compact rows, and editor controls. Remove superseded add/replace-latest UI and duplicate rendering paths.

## 4. Verification

- Compare seeded weekly values before and after the redesign: all existing columns and totals must remain unchanged.
- Verify week navigation updates both the table and worksite breakdown.
- Test multiple visits, renamed sites, identical names with different IDs, unassigned legacy sites, midnight crossings, DST, and active sessions.
- Add and edit a non-latest saved place; verify name, normalized job number, radius, and stable ID after app restart.
- Verify duplicate job numbers, invalid input, failed persistence, geofence warnings, Cancel, and unsaved-back behavior.
- Verify calendar/day navigation and session corrections without loading an endless historical card list.
- Run affected unit/UI tests, formatting checks, static analysis, compilation, and lint.
- Inspect the actual screens on an isolated Android emulator at narrow and wider phone sizes and enlarged text. Verify keyboard visibility, readable table values, and accessible controls.

## 5. Boundaries and implementation record

Work only in `C:\Users\rseba\Projects\Time_Tracker_Work_Hub` on the existing Work Hub branch. Leave the active Time Tracker checkout untouched.

Do not alter Notion, tracking classification rules, synchronization contracts, or radius limits. Do not use the S21 or connect the work phone/laptop.

During implementation, append this UI specification to the desktop plan and record completed changes and verification evidence in the desktop implementation log. No files or application state have been changed during this planning turn.
