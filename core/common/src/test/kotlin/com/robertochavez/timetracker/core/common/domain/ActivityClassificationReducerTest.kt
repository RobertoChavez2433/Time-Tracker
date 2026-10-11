package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class ActivityClassificationReducerTest {
    private val start = Instant.parse("2026-08-24T12:00:00Z")

    @Test fun `standing walking and running at an unsaved site remain idle`() {
        var state = ClassificationState()
        listOf(
            MotionActivity.STILL to MotionTransition.ENTER,
            MotionActivity.STILL to MotionTransition.EXIT,
            MotionActivity.WALKING to MotionTransition.ENTER,
            MotionActivity.RUNNING to MotionTransition.ENTER,
        ).forEachIndexed { index, (activity, transition) ->
            state = motion(state, activity, transition, index.toLong()).state
            assertEquals(ActivityBucket.IDLE, state.bucket)
        }
    }

    @Test fun `still transitions and slow GPS do not cancel recognized vehicle activity`() {
        val driving = motion(ClassificationState(), MotionActivity.IN_VEHICLE, MotionTransition.ENTER).state
        val still = motion(driving, MotionActivity.STILL, MotionTransition.ENTER, 5).state
        val leavingStill = motion(still, MotionActivity.STILL, MotionTransition.EXIT, 10).state
        val slow = position(leavingStill, 10, 70, 5.0)
        assertEquals(ActivityBucket.DRIVE, slow.bucket)
        assertEquals(ActivityBucket.DRIVE, position(slow.state, 70, 130, null, stationary = true).bucket)
    }

    @Test fun `vehicle exit and walking clear earlier GPS and vehicle evidence`() {
        val inferred = position(ClassificationState(), 0, 60, 20.0).state.copy(vehicleActive = true)
        val exited = motion(inferred, MotionActivity.IN_VEHICLE, MotionTransition.EXIT, 70)
        assertEquals(ActivityBucket.IDLE, exited.bucket)
        assertFalse(exited.state.movement.driveActive)
        val walking = motion(inferred, MotionActivity.WALKING, MotionTransition.ENTER, 70)
        assertEquals(ActivityBucket.IDLE, walking.bucket)
    }

    @Test fun `GPS can restore driving after conflicting non-driving evidence`() {
        val inferred = position(ClassificationState(), 0, 60, 20.0).state
        val exited = motion(inferred, MotionActivity.IN_VEHICLE, MotionTransition.EXIT, 70).state
        assertEquals(ActivityBucket.IDLE, position(exited, 60, 100, 20.0).bucket)
        val next = position(exited, 70, 130, 20.0)
        assertEquals(ActivityBucket.DRIVE, next.bucket)
        assertEquals(start.plusSeconds(70), next.at)
    }

    @Test fun `leaving still cannot overwrite GPS driving and continued movement stays drive`() {
        val inferred = position(ClassificationState(), 0, 60, 20.0).state
        val leavingStill = motion(inferred, MotionActivity.STILL, MotionTransition.EXIT, 70).state
        assertEquals(ActivityBucket.DRIVE, leavingStill.bucket)
        assertEquals(ActivityBucket.DRIVE, position(leavingStill, 60, 90, 20.0).bucket)
    }

    @Test fun `GPS non-driving evidence resolves idle even without a previous drive`() {
        assertEquals(ActivityBucket.IDLE, position(ClassificationState(), 0, 60, 5.0).bucket)
        assertEquals(ActivityBucket.IDLE, position(ClassificationState(), 0, 60, null, stationary = true).bucket)
    }

    @Test fun `GPS retains the fifteen mph and sixty second entry boundary`() {
        assertEquals(ActivityBucket.IDLE, position(ClassificationState(), 0, 59, 15.0).bucket)
        assertEquals(ActivityBucket.IDLE, position(ClassificationState(), 0, 60, 14.9).bucket)
        assertEquals(ActivityBucket.DRIVE, position(ClassificationState(), 0, 60, 15.0).bucket)
    }

    @Test fun `GPS lead-in includes the complete supported minute across successive samples`() {
        val first = position(ClassificationState(), 0, 30, 20.0)
        val second = position(first.state, 30, 60, 20.0)
        assertEquals(ActivityBucket.DRIVE, second.bucket)
        assertEquals(start, second.at)
    }

    @Test fun `home and site have priority over vehicle evidence`() {
        val driving = ClassificationState(vehicleActive = true)
        val site = ActivityClassificationReducer.reduce(driving, ClassificationEvent.Presence(false, true, start))
        assertEquals(ActivityBucket.SITE, site.bucket)
        val home = ActivityClassificationReducer.reduce(site.state, ClassificationEvent.Presence(true, true, start.plusSeconds(1)))
        assertEquals(ActivityBucket.HOME, home.bucket)
        assertFalse(home.state.vehicleActive)
    }

    @Test fun `delayed activity cannot replace a newer decision`() {
        val driving = motion(ClassificationState(), MotionActivity.IN_VEHICLE, MotionTransition.ENTER, 60).state
        val old = motion(driving, MotionActivity.IN_VEHICLE, MotionTransition.EXIT, 10)
        assertFalse(old.accepted)
        assertEquals(driving, old.state)
    }

    private fun motion(state: ClassificationState, activity: MotionActivity, transition: MotionTransition, seconds: Long = 0) =
        ActivityClassificationReducer.reduce(state, ClassificationEvent.Motion(activity, transition, start.plusSeconds(seconds)))

    private fun position(state: ClassificationState, from: Long, to: Long, speed: Double?, stationary: Boolean = false) =
        ActivityClassificationReducer.reduce(
            state,
            ClassificationEvent.Position(
                speed?.let { DriveMovementSegment(start.plusSeconds(from), start.plusSeconds(to), 100.0, it) },
                stationary,
                start.plusSeconds(to),
            ),
        )
}
