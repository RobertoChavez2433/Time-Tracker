# Bucket Classification Source of Truth

This document is the durable rule source for Time Tracker bucket classification. If older planning docs conflict with this file, this file wins.

## Buckets

### Home

Home is time inside the saved Home geofence. Home wins over Drive, Site, Idle, and Needs review. Miles are not counted while inside Home.

### Work

Work is total trackable time away from Home. Work remains the primary timesheet total and does not require Drive + Site + Idle to equal 100 percent when evidence is uncertain. Reports may show Work, Drive, Site, Idle, Needs review, and Miles side by side.

### Drive

Drive is away-from-home vehicle time outside saved work-site geofences. Android Activity Recognition is the preferred signal when it is available. Point-to-point speed can support Drive classification when Activity Recognition is missing, late, or unreliable. The initial inference threshold is at least 15 mph sustained for 1 minute.

### Site

Site is time inside a saved work-site geofence. Site wins over Drive minutes, so driving inside a saved work-site remains Site time. Miles driven inside Site can still count toward daily miles.

### Idle

Idle is confirmed away-from-home, outside-site, non-driving time. Uncertain time must not be silently placed into Idle.

### Needs Review

Needs review is time the app cannot confidently classify. The app must ask the user where the time belongs before changing buckets. Broad auto-fixes or hidden bucket rewrites that guess where uncertain time belongs are not allowed.

### Miles

Miles are a secondary reporting metric. Miles do not change Work totals and must not be counted inside Home. Miles inside Site may count, but that vehicle time remains Site rather than Drive.

## Priority Order

1. Home
2. Site
3. Drive
4. Idle
5. Needs review

Home and Site are geofence-driven boundaries. Drive and Idle require supporting movement or activity evidence. Needs review is the fallback when the evidence is insufficient.

## Speed Evidence Rule

The old "do not use GPS speed for drive classification" rule is revised. Time Tracker may compute point-to-point speed in memory from adjacent location samples and use that speed as supporting evidence for Drive. It must not store route geometry, speed history, or a continuous route log.

The first Drive inference rule is:

- outside Home
- outside saved work-site geofences
- movement at or above 15 mph
- sustained for at least 1 minute

## Privacy Limits

Time Tracker must not persist:

- route geometry
- latitude/longitude movement trails
- speed history
- continuous route logs

The app may persist completed bucket intervals, aggregate miles, saved geofence configuration, and user-reviewed corrections. Any movement-derived evidence must be minimized so it cannot reconstruct a route or speed history.
