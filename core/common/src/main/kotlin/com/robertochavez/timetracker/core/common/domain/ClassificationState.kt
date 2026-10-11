package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import java.time.Instant

data class ClassificationState(
    val atHome: Boolean = false,
    val atSite: Boolean = false,
    val vehicleActive: Boolean = false,
    val movement: MovementDriveInferenceState = MovementDriveInferenceState(),
    val lastEventAt: Instant? = null,
    val nonDrivingAt: Instant? = null,
) {
    val bucket: ActivityBucket
        get() = when {
            atHome -> ActivityBucket.HOME
            atSite -> ActivityBucket.SITE
            vehicleActive || movement.driveActive -> ActivityBucket.DRIVE
            else -> ActivityBucket.IDLE
        }
}

sealed interface ClassificationEvent {
    val at: Instant

    data class Motion(val activity: MotionActivity, val transition: MotionTransition, override val at: Instant) : ClassificationEvent

    data class Position(val segment: DriveMovementSegment?, val stationary: Boolean, override val at: Instant) : ClassificationEvent

    data class Presence(val atHome: Boolean, val atSite: Boolean, override val at: Instant) : ClassificationEvent
}

data class ClassificationDecision(val state: ClassificationState, val at: Instant, val reason: String, val accepted: Boolean = true) {
    val bucket: ActivityBucket get() = state.bucket
}
