package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.WorkPresence
import java.time.Duration
import java.time.Instant

enum class MotionActivity {
    IN_VEHICLE,
    STILL,
    OTHER,
}

enum class MotionTransition {
    ENTER,
    EXIT,
    OTHER,
}

enum class GeofencePresence {
    INSIDE,
    OUTSIDE,
    UNKNOWN,
}

enum class MovementEvidence {
    MOVING,
    STATIONARY,
    UNKNOWN,
}

object DriveClassificationPolicy {
    fun classify(
        activity: MotionActivity,
        transition: MotionTransition,
        homePresence: GeofencePresence,
        workPresence: GeofencePresence,
        movementEvidence: MovementEvidence = MovementEvidence.UNKNOWN,
    ): ActivityBucket = when {
        activity == MotionActivity.IN_VEHICLE &&
            transition == MotionTransition.ENTER &&
            homePresence == GeofencePresence.OUTSIDE &&
            workPresence == GeofencePresence.OUTSIDE &&
            movementEvidence != MovementEvidence.STATIONARY -> ActivityBucket.DRIVE
        activity == MotionActivity.STILL && transition == MotionTransition.ENTER -> ActivityBucket.IDLE
        else -> ActivityBucket.UNCLASSIFIED
    }

    fun workPresenceFrom(presence: WorkPresence, at: Instant, freshFor: Duration = DEFAULT_GEOFENCE_PRESENCE_FRESHNESS): GeofencePresence =
        when {
            presence.atWork -> GeofencePresence.INSIDE
            Duration.between(presence.updatedAt, at).abs() <= freshFor -> GeofencePresence.OUTSIDE
            else -> GeofencePresence.UNKNOWN
        }

    val DEFAULT_GEOFENCE_PRESENCE_FRESHNESS: Duration = Duration.ofHours(18)
}

object ActivityBucketPolicy {
    fun classify(activity: MotionActivity, transition: MotionTransition, atWork: Boolean): ActivityBucket = when {
        atWork -> DriveClassificationPolicy.classify(
            activity = activity,
            transition = transition,
            homePresence = GeofencePresence.OUTSIDE,
            workPresence = GeofencePresence.INSIDE,
        )
        else -> DriveClassificationPolicy.classify(
            activity = activity,
            transition = transition,
            homePresence = GeofencePresence.OUTSIDE,
            workPresence = GeofencePresence.OUTSIDE,
        )
    }
}
