package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MovementDriveInferenceTest {
    @Test
    fun `vehicle-like movement must be sustained for one minute before drive starts`() {
        val first = MovementDriveInference.evaluate(
            state = MovementDriveInferenceState(),
            segment = segment("2026-05-05T12:00:00Z", "2026-05-05T12:00:30Z", milesPerHour = 20.0),
            atWork = false,
        )

        assertNull(first.transition)
        assertFalse(first.shouldEscalateToVehicle)

        val second = MovementDriveInference.evaluate(
            state = first.nextState,
            segment = segment("2026-05-05T12:00:30Z", "2026-05-05T12:01:00Z", milesPerHour = 20.0),
            atWork = false,
        )

        assertEquals(ActivityBucket.DRIVE, second.transition?.bucket)
        assertEquals(Instant.parse("2026-05-05T12:00:00Z"), second.transition?.at)
        assertTrue(second.shouldEscalateToVehicle)
    }

    @Test
    fun `fifteen mph for one minute starts inferred drive`() {
        val result = MovementDriveInference.evaluate(
            state = MovementDriveInferenceState(),
            segment = segment("2026-05-05T12:00:00Z", "2026-05-05T12:01:00Z", milesPerHour = 15.0),
            atWork = false,
        )

        assertEquals(ActivityBucket.DRIVE, result.transition?.bucket)
        assertEquals(Instant.parse("2026-05-05T12:00:00Z"), result.transition?.at)
    }

    @Test
    fun `movement below fifteen mph does not start inferred drive`() {
        val result = MovementDriveInference.evaluate(
            state = MovementDriveInferenceState(),
            segment = segment("2026-05-05T12:00:00Z", "2026-05-05T12:01:00Z", milesPerHour = 14.9),
            atWork = false,
        )

        assertNull(result.transition)
        assertFalse(result.nextState.driveActive)
    }

    @Test
    fun `movement inside site ends inferred drive as site`() {
        val state = MovementDriveInferenceState(
            candidateStart = Instant.parse("2026-05-05T12:00:00Z"),
            driveActive = true,
        )

        val result = MovementDriveInference.evaluate(
            state = state,
            segment = segment("2026-05-05T12:05:00Z", "2026-05-05T12:06:00Z", milesPerHour = 20.0),
            atWork = true,
        )

        assertEquals(ActivityBucket.SITE, result.transition?.bucket)
        assertEquals(Instant.parse("2026-05-05T12:06:00Z"), result.transition?.at)
        assertTrue(result.shouldDeEscalateToBalanced)
    }

    @Test
    fun `movement below vehicle threshold ends inferred drive as idle`() {
        val state = MovementDriveInferenceState(
            candidateStart = Instant.parse("2026-05-05T12:00:00Z"),
            driveActive = true,
        )

        val result = MovementDriveInference.evaluate(
            state = state,
            segment = segment("2026-05-05T12:05:00Z", "2026-05-05T12:06:00Z", milesPerHour = 5.0),
            atWork = false,
        )

        assertEquals(ActivityBucket.IDLE, result.transition?.bucket)
        assertEquals(Instant.parse("2026-05-05T12:06:00Z"), result.transition?.at)
        assertTrue(result.shouldDeEscalateToBalanced)
    }

    private fun segment(start: String, end: String, milesPerHour: Double): DriveMovementSegment = DriveMovementSegment(
        start = Instant.parse(start),
        end = Instant.parse(end),
        distanceMeters = 1_000.0,
        speedMilesPerHour = milesPerHour,
    )
}
