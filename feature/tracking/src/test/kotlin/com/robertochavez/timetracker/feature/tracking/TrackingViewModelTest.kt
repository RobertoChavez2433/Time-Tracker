package com.robertochavez.timetracker.feature.tracking

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.logging.NoopAppLogger
import com.robertochavez.timetracker.core.testing.FakeTrackingRepository
import com.robertochavez.timetracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Instant.parse("2026-05-04T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `starts manual session at current clock instant`() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeTrackingRepository()
        val viewModel = TrackingViewModel(repository, clock, NoopAppLogger())

        viewModel.startManualSession()
        advanceUntilIdle()

        assertEquals(listOf(now), repository.manualStarts)
    }

    @Test
    fun `saves edited miles through repository contract`() = runTest(mainDispatcherRule.testDispatcher) {
        val session = AwaySession(id = "session", start = now, end = now.plusSeconds(3600))
        val repository = FakeTrackingRepository(initialSessions = listOf(session))
        val viewModel = TrackingViewModel(repository, clock, NoopAppLogger())

        viewModel.updateEditStart("session", now.toString())
        viewModel.updateEditEnd("session", now.plusSeconds(3600).toString())
        viewModel.updateEditDrivenMiles("session", "12.5")
        viewModel.saveSessionCorrections("session")
        advanceUntilIdle()

        val updated = repository.sessions.value.single()
        assertEquals(12.5, updated.drivenMiles, 0.001)
        assertTrue(updated.manuallyAdjusted)
    }

    @Test
    fun `saves edited miles without requiring untouched start and end fields`() = runTest(mainDispatcherRule.testDispatcher) {
        val end = now.plusSeconds(3600)
        val session = AwaySession(id = "session", start = now, end = end)
        val repository = FakeTrackingRepository(initialSessions = listOf(session))
        val viewModel = TrackingViewModel(repository, clock, NoopAppLogger())

        viewModel.updateEditDrivenMiles("session", "12.5")
        viewModel.saveSessionCorrections("session")
        advanceUntilIdle()

        val updated = repository.sessions.value.single()
        assertEquals(now, updated.start)
        assertEquals(end, updated.end)
        assertEquals(12.5, updated.drivenMiles, 0.001)
        assertTrue(updated.manuallyAdjusted)
    }

    @Test
    fun `session titles do not expose raw ids`() = runTest(mainDispatcherRule.testDispatcher) {
        val session = AwaySession(id = "session-raw-id", start = now.minusSeconds(3600), end = now)
        val repository = FakeTrackingRepository(initialSessions = listOf(session))
        val viewModel = TrackingViewModel(repository, clock, NoopAppLogger())

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val title = viewModel.uiState.value.sessions.single().title
        assertEquals(false, title.contains("session-raw-id"))
        assertEquals(false, title.contains("session"))
    }

    @Test
    fun `edit controls are hidden until edit is opened`() = runTest(mainDispatcherRule.testDispatcher) {
        val session = AwaySession(id = "session", start = now.minusSeconds(3600), end = now)
        val repository = FakeTrackingRepository(initialSessions = listOf(session))
        val viewModel = TrackingViewModel(repository, clock, NoopAppLogger())

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.sessions.single().isEditing)

        viewModel.editSession("session")
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.sessions.single().isEditing)
    }

    @Test
    fun `session model includes classification summary`() = runTest(mainDispatcherRule.testDispatcher) {
        val session = AwaySession(id = "session", start = now.minusSeconds(3600), end = now)
        val interval = ActivityInterval(
            id = "drive",
            sessionId = "session",
            bucket = ActivityBucket.DRIVE,
            start = now.minusSeconds(3600),
            end = now.minusSeconds(1800),
        )
        val repository = FakeTrackingRepository(initialSessions = listOf(session), initialIntervals = listOf(interval))
        val viewModel = TrackingViewModel(repository, clock, NoopAppLogger())

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals("Drive 30m", viewModel.uiState.value.sessions.single().classificationSummary)
    }
}
