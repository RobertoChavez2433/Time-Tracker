package com.robertochavez.timetracker.feature.tracking

import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.logging.NoopAppLogger
import com.robertochavez.timetracker.core.testing.FakeTrackingRepository
import com.robertochavez.timetracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
