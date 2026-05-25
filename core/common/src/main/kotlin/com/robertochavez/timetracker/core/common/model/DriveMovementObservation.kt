package com.robertochavez.timetracker.core.common.model

import java.time.Instant

data class DriveMovementObservation(
    val id: String,
    val sessionId: String,
    val start: Instant,
    val end: Instant,
    val distanceMeters: Double,
    val countedDistanceMeters: Double,
    val speedMilesPerHour: Double,
    val vehicleLike: Boolean,
    val atWork: Boolean,
) {
    init {
        require(id.isNotBlank()) { "Drive movement observation id cannot be blank." }
        require(sessionId.isNotBlank()) { "Drive movement observation session id cannot be blank." }
        require(end > start) { "Drive movement observation end must be after start." }
        require(distanceMeters >= 0.0) { "Drive movement observation distance cannot be negative." }
        require(countedDistanceMeters >= 0.0) { "Drive movement observation counted distance cannot be negative." }
    }
}

data class DriveMovementObservationDraft(
    val start: Instant,
    val end: Instant,
    val distanceMeters: Double,
    val countedDistanceMeters: Double,
    val speedMilesPerHour: Double,
    val vehicleLike: Boolean,
    val atWork: Boolean,
) {
    init {
        require(end > start) { "Drive movement observation end must be after start." }
        require(distanceMeters >= 0.0) { "Drive movement observation distance cannot be negative." }
        require(countedDistanceMeters >= 0.0) { "Drive movement observation counted distance cannot be negative." }
    }
}
