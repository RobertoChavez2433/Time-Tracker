# Bucket Classification Source of Truth

Updated August 30, 2026 after the user explicitly approved removing Review and including other away time in Idle. This supersedes the former requirement to ask about uncertain time and any conflicting historical plan.

## Reporting rules

- Home is time inside the saved Home geofence. Home wins over Drive, Site, and Idle.
- Work is total trackable time away from Home. Work = Drive + Site + Idle, measured before display rounding.
- Site is time inside a saved worksite. Site wins over Drive minutes. Driving within Site may still contribute miles.
- Drive is supported vehicle time away from Home and outside a saved Site.
- Idle is away time not identified as driving or at a saved worksite. It includes walking, working at unsaved locations, legacy Review intervals, and activity gaps.
- Needs review is a legacy storage/compatibility value only. New classification never generates it. Reports expose zero Review duration and do not display a Review column.
- Miles remain a separate metric. This change does not alter historical miles or infer historical driving.

Calculate the partition with merged, non-overlapping ranges: remove Home first, constrain Site and Drive to Work, give Site priority, then calculate Idle = Work - Drive - Site. Respect excluded sessions, work schedules, midnight, and daylight-saving transitions. Never rewrite historical activity records to obtain this presentation.

## Live evidence

One shared classification coordinator serializes activity, GPS, and geofence evidence. Its pure reducer uses Home, Site, supported Drive, then Idle. Unknown outside-site presence does not discard positive vehicle evidence.

Walking/running entry and vehicle exit clear older vehicle evidence. STILL exit preserves the existing decision. A slow GPS segment cannot cancel an active Activity Recognition vehicle state. GPS may establish Drive again after new supporting evidence.

The point-to-point speed entry rule remains 15 mph sustained for 1 minute. Mileage thresholds and distance counting remain unchanged. Stationary accepted location evidence can resolve GPS-only driving without a preceding new vehicle transition. Lack of a new location callback is not evidence of movement or of a stop.

Create an initial Idle interval with the session. Coalesce identical decisions. Ignore events before the active session or newer evidence, and constrain GPS lead-in to the open interval. Closed records are never changed by a late callback. Persist only current session-scoped evidence and reset it on session end/restore; never apply it to another session.

## Compatibility and privacy

Keep legacy NEEDS_REVIEW database values and old plain JSON backups readable. Keep machine-facing needsReviewMinutes and unclassifiedMinutes fields at zero in new report summaries. No schema migration or historical row conversion is needed. No remote property deletion or automatic sync activation is authorized.

Do not persist route geometry, speed history, or a continuous route log. Current classification state contains only flags, decision timestamps, and a session ID. Diagnostic reason codes contain no location trail. The existing single mileage baseline is not expanded into a history.

## Weekly UI

Show Day, Home, Work, Drive, Site, Idle, Miles. All seven days and the totals row remain visible without table scrolling. Only the header and totals row have a different background; all day rows, including today, use the same background. The Idle explanation is: "Away time not identified as driving or at a saved worksite."
