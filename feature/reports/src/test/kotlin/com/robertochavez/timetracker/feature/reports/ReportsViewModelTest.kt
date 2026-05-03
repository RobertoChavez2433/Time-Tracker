package com.robertochavez.timetracker.feature.reports

import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.testing.FakePayPeriodSettingsRepository
import com.robertochavez.timetracker.core.testing.FakeTrackingRepository
import com.robertochavez.timetracker.core.testing.FakeWorkScheduleRepository
import com.robertochavez.timetracker.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("America/New_York")
    private val now = Instant.parse("2026-05-04T16:00:00Z")
    private val clock = Clock.fixed(now, zone)

    @Test
    fun `reports include away time and driven miles`() = runTest(mainDispatcherRule.testDispatcher) {
        val session = AwaySession(
            id = "session",
            start = Instant.parse("2026-05-04T13:00:00Z"),
            end = Instant.parse("2026-05-04T15:00:00Z"),
            drivenMiles = 22.5,
        )
        val viewModel = ReportsViewModel(
            trackingRepository = FakeTrackingRepository(initialSessions = listOf(session)),
            workScheduleRepository = FakeWorkScheduleRepository(),
            payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
            clock = clock,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val today = viewModel.uiState.value.reports.first()
        assertEquals("2h 0m", today.away)
        assertEquals("22.5 mi", today.miles)
    }
}
