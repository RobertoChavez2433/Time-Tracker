package com.robertochavez.timetracker.core.common.domain

import java.time.Instant

object ActivityClassificationReducer {
    fun reduce(state: ClassificationState, event: ClassificationEvent): ClassificationDecision {
        if (state.lastEventAt?.isAfter(event.at) == true) {
            return ClassificationDecision(state, event.at, "late_event_ignored", accepted = false)
        }
        val decision = when (event) {
            is ClassificationEvent.Motion -> motion(state, event)
            is ClassificationEvent.Position -> position(state, event)
            is ClassificationEvent.Presence -> ClassificationDecision(
                if (event.atHome) ClassificationState(atHome = true) else state.copy(atHome = false, atSite = event.atSite),
                event.at,
                "geofence_presence",
            )
        }
        return decision.copy(state = decision.state.copy(lastEventAt = event.at))
    }

    private fun motion(state: ClassificationState, event: ClassificationEvent.Motion): ClassificationDecision {
        val entering = event.transition == MotionTransition.ENTER
        val vehicleExit = event.activity == MotionActivity.IN_VEHICLE && event.transition == MotionTransition.EXIT
        val onFoot = entering && (event.activity == MotionActivity.WALKING || event.activity == MotionActivity.RUNNING)
        val stopped = entering && event.activity == MotionActivity.STILL && !state.vehicleActive
        val next = when {
            entering && event.activity == MotionActivity.IN_VEHICLE -> state.copy(vehicleActive = true)
            vehicleExit || onFoot || stopped -> state.copy(
                vehicleActive = false,
                movement = MovementDriveInferenceState(),
                nonDrivingAt = event.at,
            )
            else -> state
        }
        return ClassificationDecision(next, event.at, "motion_${event.activity.name.lowercase()}_${event.transition.name.lowercase()}")
    }

    private fun position(state: ClassificationState, event: ClassificationEvent.Position): ClassificationDecision {
        val segment = event.segment?.after(state.nonDrivingAt)
        if (segment == null) {
            val next = if (event.stationary) state.copy(movement = MovementDriveInferenceState()) else state
            return ClassificationDecision(next, event.at, if (event.stationary) "stationary_position" else "position_without_movement")
        }
        val inferred = MovementDriveInference.evaluate(state.movement, segment, state.atSite || state.atHome)
        val next = state.copy(movement = inferred.nextState)
        // Only the still-open interval can gain the supported GPS lead-in.
        val start = inferred.transition?.at ?: event.at
        return ClassificationDecision(next, start, if (next.movement.driveActive) "sustained_vehicle_movement" else "non_vehicle_movement")
    }

    private fun DriveMovementSegment.after(boundary: Instant?): DriveMovementSegment? = when {
        boundary == null || boundary <= start -> this
        boundary >= end -> null
        else -> copy(start = boundary)
    }
}
