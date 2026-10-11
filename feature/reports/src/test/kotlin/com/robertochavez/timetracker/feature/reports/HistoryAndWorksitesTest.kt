package com.robertochavez.timetracker.feature.reports

import androidx.lifecycle.SavedStateHandle
import com.robertochavez.timetracker.core.common.model.DateRange
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
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
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryAndWorksitesTest {
    @get:Rule val dispatchers = MainDispatcherRule()
    private val zone = ZoneId.of("America/New_York")
    private val now = Instant.parse("2026-08-30T16:00:00Z")
    private val clock = Clock.fixed(now, zone)

    @Test fun `same names stay separate and renamed visits retain snapshots`() {
        val visits = listOf(
            visit("a1", "a", "Old name", "2026-08-25T12:00:00Z", "2026-08-25T13:00:00Z", "JOB-1"),
            visit("a2", "a", "Same name", "2026-08-26T12:00:00Z", "2026-08-26T14:00:00Z", "JOB-1"),
            visit("b1", "b", "Same name", "2026-08-26T14:00:00Z", "2026-08-26T15:00:00Z"),
        )
        val groups = visits.worksiteSummaries(weekRange(LocalDate.parse("2026-08-30")), now, zone)
        assertEquals(2, groups.size)
        val renamed = groups.single { it.id == "a" }
        assertEquals("3h", renamed.total)
        assertEquals(listOf("Same name", "Old name"), renamed.visits.map { it.name })
        assertEquals(null, groups.single { it.id == "b" }.projectNumber)
    }

    @Test fun `midnight and both DST transitions count elapsed onsite time`() {
        val cases = listOf(
            Triple("2026-03-08T05:00:00Z", "2026-03-09T04:00:00Z", "23h"),
            Triple("2026-11-01T04:00:00Z", "2026-11-02T05:00:00Z", "25h"),
        )
        cases.forEach { (start, end, total) ->
            val date = Instant.parse(start).atZone(zone).toLocalDate()
            val groups = listOf(
                visit("v", "s", "Site", start, end),
            ).worksiteSummaries(DateRange(date, date.plusDays(1)), Instant.parse(end), zone)
            assertEquals(total, groups.single().total)
            assertEquals(1, groups.single().visits.size)
        }
        val crossing = listOf(visit("v", "s", "Site", "2026-08-25T03:00:00Z", "2026-08-25T05:00:00Z"))
        val summary = crossing.worksiteSummaries(weekRange(LocalDate.parse("2026-08-25")), now, zone).single()
        assertEquals(2, summary.visits.size)
        assertEquals(listOf("1h", "1h"), summary.visits.map { it.duration.formatHours() })
    }

    @Test fun `active visits accrue without zero-length splitter errors and clip to selected week`() {
        val active = visit("v", "s", "Site", "2026-08-30T15:00:00Z", null)
        val range = weekRange(LocalDate.parse("2026-08-30"))
        assertTrue(listOf(active).worksiteSummaries(range, active.start, zone).isEmpty())
        assertEquals("1h", listOf(active).worksiteSummaries(range, now, zone).single().total)
        assertTrue(listOf(active).worksiteSummaries(weekRange(LocalDate.parse("2026-08-23")), now, zone).isEmpty())
    }

    @Test fun `history defaults to current month and year renders only one navigable month`() = runTest(dispatchers.testDispatcher) {
        val settings = FakePayPeriodSettingsRepository()
        val source = source()
        val saved = SavedStateHandle()
        val vm = HistoryViewModel(source, settings, clock, saved)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        advanceUntilIdle()
        assertEquals(HistoryRangeMode.MONTH, vm.uiState.value.mode)
        assertEquals("2026-08", vm.uiState.value.month)
        vm.selectRange(HistoryRangeMode.YEAR)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.calendar.weeks.size in 4..6)
        vm.previousMonth()
        advanceUntilIdle()
        assertEquals("2026-07", vm.uiState.value.month)
        val recreated = HistoryViewModel(source, settings, clock, saved)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { recreated.uiState.collect() }
        advanceUntilIdle()
        assertEquals(HistoryRangeMode.YEAR, recreated.uiState.value.mode)
        assertEquals("2026-07", recreated.uiState.value.month)
        vm.selectRange(HistoryRangeMode.CUSTOM)
        vm.updateStart("invalid")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error.isNotBlank())
    }

    @Test fun `selected day queries only the requested day`() = runTest(dispatchers.testDispatcher) {
        val tracking = FakeTrackingRepository()
        val vm = DayDetailViewModel(source(tracking), clock, SavedStateHandle(mapOf("date" to "2026-08-25")))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        advanceUntilIdle()
        assertEquals("2026-08-25", vm.uiState.value.date)
        assertEquals(Duration.ofDays(1), tracking.sessionRangeRequests.single().let { Duration.between(it.first, it.second) })
    }

    private fun source(tracking: FakeTrackingRepository = FakeTrackingRepository()) = ReportDataSource(
        tracking,
        FakeWorkScheduleRepository(),
        FakePayPeriodSettingsRepository(),
        FakeWorkSiteSessionRepository(),
        clock,
    )

    private fun visit(id: String, site: String, name: String, start: String, end: String?, job: String? = null) = WorkSiteSession(
        id = id,
        workLocationId = site,
        start = Instant.parse(start),
        end = end?.let(Instant::parse),
        workLocationLabelSnapshot = name,
        projectNumberSnapshot = job,
    )
}
