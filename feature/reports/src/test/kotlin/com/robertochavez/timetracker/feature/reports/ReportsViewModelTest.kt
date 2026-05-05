package com.robertochavez.timetracker.feature.reports

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.logging.NoopAppLogger
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
    fun `dashboard includes weekly ledger with rounded miles`() = runTest(mainDispatcherRule.testDispatcher) {
        val session = AwaySession(
            id = "session",
            start = Instant.parse("2026-05-04T13:00:00Z"),
            end = Instant.parse("2026-05-04T15:00:00Z"),
            drivenMiles = 22.5,
        )
        val intervals = listOf(
            ActivityInterval(
                id = "drive",
                sessionId = session.id,
                bucket = ActivityBucket.DRIVE,
                start = Instant.parse("2026-05-04T13:00:00Z"),
                end = Instant.parse("2026-05-04T14:00:00Z"),
            ),
            ActivityInterval(
                id = "idle",
                sessionId = session.id,
                bucket = ActivityBucket.IDLE,
                start = Instant.parse("2026-05-04T14:00:00Z"),
                end = Instant.parse("2026-05-04T15:00:00Z"),
            ),
        )
        val viewModel = ReportsViewModel(
            trackingRepository = FakeTrackingRepository(initialSessions = listOf(session), initialIntervals = intervals),
            workScheduleRepository = FakeWorkScheduleRepository(),
            payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val today = viewModel.uiState.value.summaries.first()
        assertEquals("2h", today.home)
        assertEquals("2h", today.work)
        assertEquals("23", today.miles)

        val monday = viewModel.uiState.value.weeklyLedger.rows.first()
        assertEquals("Mon", monday.day)
        assertEquals("5/4", monday.date)
        assertEquals("2h", monday.home)
        assertEquals("2h", monday.work)
        assertEquals("1h", monday.drive)
        assertEquals("1h", monday.onSite)
        assertEquals("23", monday.miles)
    }
}
