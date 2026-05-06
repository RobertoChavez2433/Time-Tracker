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
        homeLatitude = if (homeSet) 42.3314 else null,
        homeLongitude = if (homeSet) -83.0458 else null,
        homeRadiusMeters = if (homeSet) 150f else null,
        workSet = false,
        workLocationCount = 0,
        workLocations = emptyList(),
        workLatitude = null,
        workLongitude = null,
        workRadiusMeters = null,
        atWork = false,
        workSiteSessionCount = 0,
        activeSession = activeSession,
        latestSession = activeSession,
        sessionCount = if (activeSession == null) 0 else 1,
        countedSessionCount = if (activeSession == null) 0 else 1,
        manuallyAdjustedSessionCount = 0,
        totalDrivenMiles = activeSession?.drivenMiles ?: 0.0,
        activityIntervalCount = 0,
        trackableToday = true,
        todayDayOfWeek = "SUNDAY",
        workdayCount = 5,
        workdays = emptyMap(),
        payPeriodAnchorDate = "2026-05-03",
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
