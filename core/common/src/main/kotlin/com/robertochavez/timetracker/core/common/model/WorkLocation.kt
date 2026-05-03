package com.robertochavez.timetracker.core.common.model

import java.time.Instant

data class WorkLocation(val latitude: Double, val longitude: Double, val radiusMeters: Float, val updatedAt: Instant) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }
        require(radiusMeters >= MINIMUM_RADIUS_METERS) {
            "Work geofence radius must be at least $MINIMUM_RADIUS_METERS meters."
        }
        require(radiusMeters <= MAXIMUM_RADIUS_METERS) {
            "Work geofence radius must be no more than $MAXIMUM_RADIUS_METERS meters."
        }
    }

    companion object {
        const val MINIMUM_RADIUS_METERS = 100f
        const val MAXIMUM_RADIUS_METERS = 8046.72f
    }
}
