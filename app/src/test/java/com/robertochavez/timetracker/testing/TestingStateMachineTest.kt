package com.robertochavez.timetracker.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestingStateMachineTest {
    @Test
    fun `state blocks verification when setup is incomplete`() {
        val state = TestingStateMachine.evaluate(snapshot(homeSet = false, privacyAccepted = false))

        assertEquals("setupRequired", state.posture)
        assertFalse(state.interactionReady)
        assertTrue("home_location_missing" in state.interactionBlockers)
        assertTrue("privacy_disclosure_pending" in state.interactionBlockers)
    }

    @Test
    fun `state is tracking when setup is complete and session is active`() {
        val state = TestingStateMachine.evaluate(snapshot(activeSession = activeSession()))

        assertEquals("tracking", state.posture)
        assertTrue(state.interactionReady)
    }

    private fun snapshot(
        homeSet: Boolean = true,
        privacyAccepted: Boolean = true,
        activeSession: AwaySessionSummary? = null,
    ): AppStateSnapshot = AppStateSnapshot(
        runId = "test",
        actorId = "local",
        capturedAtEpochMillis = 0L,
        homeSet = homeSet,
        homeRadiusMeters = if (homeSet) 150f else null,
        workSet = false,
        workRadiusMeters = null,
        atWork = false,
        activeSession = activeSession,
        sessionCount = if (activeSession == null) 0 else 1,
        activityIntervalCount = 0,
        trackableToday = true,
        workdayCount = 5,
        privacyDisclosureAccepted = privacyAccepted,
        minimalActiveNotificationEnabled = false,
        liveTimerNotificationEnabled = false,
        recentLogCount = 0,
    )

    private fun activeSession(): AwaySessionSummary = AwaySessionSummary(
        idPrefix = "session",
        startEpochMillis = 0L,
        endEpochMillis = null,
        drivenMiles = 0.0,
    )
}
