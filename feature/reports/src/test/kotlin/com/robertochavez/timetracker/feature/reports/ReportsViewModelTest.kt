package com.robertochavez.timetracker.feature.reports

import androidx.lifecycle.SavedStateHandle
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import com.robertochavez.timetracker.core.logging.NoopAppLogger
import com.robertochavez.timetracker.core.testing.FakePayPeriodSettingsRepository
import com.robertochavez.timetracker.core.testing.FakeTrackingRepository
import com.robertochavez.timetracker.core.testing.FakeWorkScheduleRepository
import com.robertochavez.timetracker.core.testing.FakeWorkSiteSessionRepository
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class ReportsViewModelTest {
    @Test
    fun `reports expose guarded week navigation contract`() {
        val viewModelMethods = ReportsViewModel::class.java.methods.map { it.name }.toSet()
        val stateMethods = ReportsUiState::class.java.methods.map { it.name }.toSet()

        assertTrue("ReportsViewModel must expose previousWeek", "previousWeek" in viewModelMethods)
        assertTrue("ReportsViewModel must expose nextWeek", "nextWeek" in viewModelMethods)
        assertTrue("ReportsUiState must expose selected week offset", "getSelectedWeekOffset" in stateMethods)
        assertTrue("ReportsUiState must expose next-week availability", "getCanNavigateToNextWeek" in stateMethods)
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("America/New_York")
    private val now = Instant.parse("2026-05-04T16:00:00Z")
    private val clock = Clock.fixed(now, zone)

    @Test
    fun `week browsing is bounded and uses independent fixed-size queries`() = runTest(mainDispatcherRule.testDispatcher) {
        val trackingRepository = FakeTrackingRepository()
        val viewModel = ReportsViewModel(
            trackingRepository = trackingRepository,
            workScheduleRepository = FakeWorkScheduleRepository(),
            payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
            workSiteSessionRepository = FakeWorkSiteSessionRepository(),
            clock = clock,
            logger = NoopAppLogger(),
            savedStateHandle = SavedStateHandle(mapOf("selected_week_offset" to 4)),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.selectedWeekOffset)
        assertTrue(!viewModel.uiState.value.canNavigateToNextWeek)

        repeat(30) { viewModel.previousWeek() }
        advanceUntilIdle()

        assertEquals(-30, viewModel.uiState.value.selectedWeekOffset)
        assertEquals(listOf("Oct 6 - Oct 12, 2025"), viewModel.uiState.value.summaries.map { it.title })
        assertEquals("Oct 6 - Oct 12, 2025", viewModel.uiState.value.weeklyLedger.title)
        assertEquals(DashboardSummaryMode.SELECTED_WEEK, viewModel.uiState.value.summaryMode)
        assertTrue(viewModel.uiState.value.canNavigateToNextWeek)
        assertTrue(
            trackingRepository.sessionRangeRequests.all { (start, end) ->
                Duration.between(start, end) <= Duration.ofDays(14)
            },
        )

        repeat(40) { viewModel.nextWeek() }
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.selectedWeekOffset)
        assertTrue(!viewModel.uiState.value.canNavigateToNextWeek)
        assertEquals(listOf("Today", "Week", "Pay Period"), viewModel.uiState.value.summaries.map { it.title })
    }

    @Test
    fun `dashboard partitions outside home time into drive site and idle buckets`() = runTest(mainDispatcherRule.testDispatcher) {
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
        val siteSessions = listOf(
            WorkSiteSession(
                id = "site",
                workLocationId = "work",
                start = Instant.parse("2026-05-04T14:00:00Z"),
                end = Instant.parse("2026-05-04T14:30:00Z"),
            ),
        )
        val viewModel = ReportsViewModel(
            trackingRepository = FakeTrackingRepository(initialSessions = listOf(session), initialIntervals = intervals),
            workScheduleRepository = FakeWorkScheduleRepository(),
            payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
            workSiteSessionRepository = FakeWorkSiteSessionRepository(siteSessions),
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val today = viewModel.uiState.value.summaries.first()
        assertEquals("10h", today.home)
        assertEquals("2h", today.work)
        assertEquals("1h", today.drive)
        assertEquals("0m", today.needsReview)
        assertEquals("23", today.miles)

        val monday = viewModel.uiState.value.weeklyLedger.rows.first()
        assertEquals("Mon", monday.day)
        assertEquals("5/4", monday.date)
        assertEquals("10h", monday.home)
        assertEquals("2h", monday.work)
        assertEquals("1h", monday.drive)
        assertEquals("30m", monday.site)
        assertEquals("30m", monday.idle)
        assertEquals("0m", monday.needsReview)
        assertEquals("23", monday.miles)
    }

    @Test
    fun `pull to refresh recomputes active totals without repository changes`() = runTest(mainDispatcherRule.testDispatcher) {
        val mutableClock = MutableClock(
            instant = Instant.parse("2026-05-04T16:00:00Z"),
            zone = ZoneOffset.UTC,
        )
        val session = AwaySession(
            id = "active",
            start = Instant.parse("2026-05-04T15:30:00Z"),
            end = null,
        )
        val viewModel = ReportsViewModel(
            trackingRepository = FakeTrackingRepository(initialSessions = listOf(session)),
            workScheduleRepository = FakeWorkScheduleRepository(),
            payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
            workSiteSessionRepository = FakeWorkSiteSessionRepository(),
            clock = mutableClock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        assertEquals("30m", viewModel.uiState.value.summaries.first().work)

        mutableClock.instant = Instant.parse("2026-05-04T17:00:00Z")
        viewModel.refreshDashboard()
        advanceUntilIdle()

        assertEquals("1h 30m", viewModel.uiState.value.summaries.first().work)
    }

    @Test
    fun `resume recomputes active totals without repository changes`() = runTest(mainDispatcherRule.testDispatcher) {
        val mutableClock = MutableClock(
            instant = Instant.parse("2026-05-04T16:00:00Z"),
            zone = ZoneOffset.UTC,
        )
        val session = AwaySession(
            id = "active",
            start = Instant.parse("2026-05-04T15:30:00Z"),
            end = null,
        )
        val viewModel = ReportsViewModel(
            trackingRepository = FakeTrackingRepository(initialSessions = listOf(session)),
            workScheduleRepository = FakeWorkScheduleRepository(),
            payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
            workSiteSessionRepository = FakeWorkSiteSessionRepository(),
            clock = mutableClock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        assertEquals("30m", viewModel.uiState.value.summaries.first().work)

        mutableClock.instant = Instant.parse("2026-05-04T17:00:00Z")
        viewModel.refreshForResume()
        advanceUntilIdle()

        assertEquals("1h 30m", viewModel.uiState.value.summaries.first().work)
    }

    @Test
    @Suppress("LongMethod")
    fun `history groups site visits by label and filters by site`() = runTest(mainDispatcherRule.testDispatcher) {
        val siteSessions = listOf(
            WorkSiteSession(
                id = "office-monday",
                workLocationId = "office",
                workLocationLabelSnapshot = "Office",
                start = Instant.parse("2026-05-04T13:00:00Z"),
                end = Instant.parse("2026-05-04T15:00:00Z"),
            ),
            WorkSiteSession(
                id = "yard-friday",
                workLocationId = "yard",
                workLocationLabelSnapshot = "North Yard",
                start = Instant.parse("2026-05-01T14:00:00Z"),
                end = Instant.parse("2026-05-01T15:30:00Z"),
            ),
        )
        val officeTrip = AwaySession(
            id = "office-trip",
            start = Instant.parse("2026-05-04T12:00:00Z"),
            end = Instant.parse("2026-05-04T16:00:00Z"),
            drivenMiles = 22.5,
        )
        val officeDrive = ActivityInterval(
            id = "office-drive",
            sessionId = officeTrip.id,
            bucket = ActivityBucket.DRIVE,
            start = Instant.parse("2026-05-04T12:00:00Z"),
            end = Instant.parse("2026-05-04T13:00:00Z"),
        )
        val trackingRepository = FakeTrackingRepository(initialSessions = listOf(officeTrip), initialIntervals = listOf(officeDrive))
        val siteSessionRepository = FakeWorkSiteSessionRepository(siteSessions)
        val viewModel = ReportsViewModel(
            trackingRepository = trackingRepository,
            workScheduleRepository = FakeWorkScheduleRepository(),
            payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
            workSiteSessionRepository = siteSessionRepository,
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertEquals(HistoryRangeMode.PAY_PERIOD, viewModel.uiState.value.historyRangeMode)
        assertEquals("Biweekly Pay Period: May 4, 2026 - May 17, 2026", viewModel.uiState.value.historyTitle)
        assertEquals(listOf("Office"), viewModel.uiState.value.siteHistory.map { it.label })
        assertEquals(2, viewModel.uiState.value.historyCalendar.weeks.size)
        val payPeriodDay = viewModel.uiState.value.historyCalendar.dailyRows.single()
        assertEquals("2026-05-04", payPeriodDay.dateIso)
        assertEquals("Mon, May 4", payPeriodDay.dayLabel)
        assertEquals("8h", payPeriodDay.home)
        assertEquals("4h", payPeriodDay.work)
        assertEquals("1h", payPeriodDay.drive)
        assertEquals("23", payPeriodDay.miles)
        assertEquals(
            Instant.parse("2026-05-04T04:00:00Z") to Instant.parse("2026-05-18T04:00:00Z"),
            trackingRepository.sessionRangeRequests.last(),
        )
        assertEquals(
            trackingRepository.sessionRangeRequests.last(),
            trackingRepository.intervalRangeRequests.last(),
        )
        assertEquals(
            trackingRepository.sessionRangeRequests.last(),
            siteSessionRepository.rangeRequests.last(),
        )

        viewModel.selectHistoryRange(HistoryRangeMode.MONTH)
        advanceUntilIdle()

        assertEquals("May 2026", viewModel.uiState.value.historyTitle)
        assertEquals(listOf("Office", "North Yard"), viewModel.uiState.value.siteHistory.map { it.label })
        val office = viewModel.uiState.value.siteHistory.first { it.label == "Office" }
        assertEquals("2h", office.total)
        assertEquals("1", office.visitCount)
        assertEquals("1h", office.drive)
        assertEquals("23", office.miles)
        assertEquals("Mon, May 4", office.dailyRows.single().date)
        assertEquals(
            Instant.parse("2026-05-01T04:00:00Z") to Instant.parse("2026-06-01T04:00:00Z"),
            trackingRepository.sessionRangeRequests.last(),
        )

        viewModel.selectHistoryRange(HistoryRangeMode.WEEK)
        advanceUntilIdle()

        assertEquals("Week: May 4, 2026 - May 10, 2026", viewModel.uiState.value.historyTitle)
        assertEquals(1, viewModel.uiState.value.historyCalendar.weeks.size)
        assertEquals(listOf("2026-05-04"), viewModel.uiState.value.historyCalendar.dailyRows.map { it.dateIso })

        viewModel.updateSiteFilter("office")
        advanceUntilIdle()

        assertEquals(listOf("Office"), viewModel.uiState.value.siteHistory.map { it.label })
    }

    @Test
    fun `custom history range drives selected totals and calendar days`() = runTest(mainDispatcherRule.testDispatcher) {
        val sessions = listOf(
            AwaySession(
                id = "friday",
                start = Instant.parse("2026-05-01T14:00:00Z"),
                end = Instant.parse("2026-05-01T16:00:00Z"),
                drivenMiles = 12.0,
            ),
            AwaySession(
                id = "monday",
                start = Instant.parse("2026-05-04T13:00:00Z"),
                end = Instant.parse("2026-05-04T15:00:00Z"),
                drivenMiles = 24.0,
            ),
        )
        val intervals = listOf(
            ActivityInterval(
                id = "friday-drive",
                sessionId = "friday",
                bucket = ActivityBucket.DRIVE,
                start = Instant.parse("2026-05-01T14:00:00Z"),
                end = Instant.parse("2026-05-01T15:00:00Z"),
            ),
            ActivityInterval(
                id = "monday-drive",
                sessionId = "monday",
                bucket = ActivityBucket.DRIVE,
                start = Instant.parse("2026-05-04T13:00:00Z"),
                end = Instant.parse("2026-05-04T14:00:00Z"),
            ),
        )
        val viewModel = ReportsViewModel(
            trackingRepository = FakeTrackingRepository(initialSessions = sessions, initialIntervals = intervals),
            workScheduleRepository = FakeWorkScheduleRepository(),
            payPeriodSettingsRepository = FakePayPeriodSettingsRepository(),
            workSiteSessionRepository = FakeWorkSiteSessionRepository(),
            clock = clock,
            logger = NoopAppLogger(),
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.selectHistoryRange(HistoryRangeMode.CUSTOM)
        viewModel.updateCustomStartDate("2026-05-01")
        viewModel.updateCustomEndDate("2026-05-01")
        advanceUntilIdle()

        assertEquals("2026-05-01 to 2026-05-01", viewModel.uiState.value.historyTitle)
        assertEquals("12", viewModel.uiState.value.historySummary.miles)
        assertEquals(listOf("2026-05-01"), viewModel.uiState.value.historyCalendar.dailyRows.map { it.dateIso })
        val friday = viewModel.uiState.value.historyCalendar.dailyRows.single()
        assertEquals("2h", friday.work)
        assertEquals("1h", friday.drive)
        assertEquals("12", friday.miles)
    }
}

private class MutableClock(var instant: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

    override fun instant(): Instant = instant
}
