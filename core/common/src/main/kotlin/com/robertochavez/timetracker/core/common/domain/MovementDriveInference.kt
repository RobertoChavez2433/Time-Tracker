package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import java.time.Duration
import java.time.Instant

data class MovementDriveInferenceState(val candidateStart: Instant? = null, val driveActive: Boolean = false)

data class MovementBucketTransition(val bucket: ActivityBucket, val at: Instant)

data class MovementDriveInferenceResult(
    val nextState: MovementDriveInferenceState,
    val transition: MovementBucketTransition? = null,
    val shouldEscalateToVehicle: Boolean = false,
    val shouldDeEscalateToBalanced: Boolean = false,
)

object MovementDriveInference {
    val SUSTAINED_DRIVE_DURATION: Duration = Duration.ofMinutes(1)

    fun evaluate(state: MovementDriveInferenceState, segment: DriveMovementSegment, atWork: Boolean): MovementDriveInferenceResult = when {
        atWork -> state.stopDriving(ActivityBucket.SITE, segment.end)
        segment.isVehicleLike -> state.continueVehicleLikeMovement(segment)
        else -> state.stopDriving(ActivityBucket.IDLE, segment.end)
    }

    private fun MovementDriveInferenceState.continueVehicleLikeMovement(segment: DriveMovementSegment): MovementDriveInferenceResult {
        val start = candidateStart ?: segment.start
        val nextState = copy(candidateStart = start)
        val sustainedDuration = Duration.between(start, segment.end)
        return if (!driveActive && sustainedDuration >= SUSTAINED_DRIVE_DURATION) {
            val earliestSupportedDriveStart = segment.end.minus(SUSTAINED_DRIVE_DURATION)
            val transitionAt = if (earliestSupportedDriveStart.isAfter(start)) earliestSupportedDriveStart else start
            MovementDriveInferenceResult(
                nextState = nextState.copy(driveActive = true),
                transition = MovementBucketTransition(ActivityBucket.DRIVE, transitionAt),
                shouldEscalateToVehicle = true,
            )
        } else {
            MovementDriveInferenceResult(nextState = nextState)
        }
    }

    private fun MovementDriveInferenceState.stopDriving(bucket: ActivityBucket, at: Instant): MovementDriveInferenceResult =
        MovementDriveInferenceResult(
            nextState = MovementDriveInferenceState(),
            transition = if (driveActive) MovementBucketTransition(bucket, at) else null,
            shouldDeEscalateToBalanced = driveActive,
        )
}
