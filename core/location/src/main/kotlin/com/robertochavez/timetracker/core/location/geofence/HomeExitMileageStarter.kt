package com.robertochavez.timetracker.core.location.geofence

import com.robertochavez.timetracker.core.location.mileage.DriveMileageTracker

internal suspend fun startDriveMileageForHomeExit(
    awaySessionStarted: Boolean,
    alreadyAway: Boolean,
    atWork: Boolean,
    driveMileageTracker: DriveMileageTracker,
) {
    if (awaySessionStarted && !alreadyAway && !atWork) {
        driveMileageTracker.clearBaseline()
        driveMileageTracker.startTracking()
    }
}
