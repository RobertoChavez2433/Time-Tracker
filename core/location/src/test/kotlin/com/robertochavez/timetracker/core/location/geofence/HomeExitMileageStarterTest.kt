package com.robertochavez.timetracker.core.location.geofence

import android.location.Location
import com.robertochavez.timetracker.core.location.mileage.DriveMileageTracker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeExitMileageStarterTest {
    @Test
    fun `new home exit clears baseline before starting vehicle tracking`() = runTest {
        val tracker = RecordingDriveMileageTracker()

        startDriveMileageForHomeExit(
            awaySessionStarted = true,
            alreadyAway = false,
            atWork = false,
            driveMileageTracker = tracker,
        )

        assertEquals(listOf("clearBaseline", "startTracking"), tracker.calls)
    }

    @Test
    fun `home exit does not restart mileage tracking when already away`() = runTest {
        val tracker = RecordingDriveMileageTracker()

        startDriveMileageForHomeExit(
            awaySessionStarted = true,
            alreadyAway = true,
            atWork = false,
            driveMileageTracker = tracker,
        )

        assertEquals(emptyList<String>(), tracker.calls)
    }

    @Test
    fun `home exit does not start mileage tracking while already at work`() = runTest {
        val tracker = RecordingDriveMileageTracker()

        startDriveMileageForHomeExit(
            awaySessionStarted = true,
            alreadyAway = false,
            atWork = true,
            driveMileageTracker = tracker,
        )

        assertEquals(emptyList<String>(), tracker.calls)
    }
}

private class RecordingDriveMileageTracker : DriveMileageTracker {
    val calls = mutableListOf<String>()

    override suspend fun startAwayTracking() {
        calls += "startAwayTracking"
    }

    override suspend fun startTracking() {
        calls += "startTracking"
    }

    override suspend fun stopTracking() {
        calls += "stopTracking"
    }

    override suspend fun clearBaseline() {
        calls += "clearBaseline"
    }

    override suspend fun recordLocation(location: Location) {
        calls += "recordLocation"
    }
}
