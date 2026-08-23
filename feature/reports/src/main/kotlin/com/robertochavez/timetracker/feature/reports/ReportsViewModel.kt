package com.robertochavez.timetracker.feature.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.domain.ReportCalculator
import com.robertochavez.timetracker.core.common.domain.payPeriodRangeFor
import com.robertochavez.timetracker.core.common.model.DateRange
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.UiDebugState
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.common.repository.WorkSiteSessionRepository
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
@Suppress("LargeClass")
class ReportsViewModel @Inject constructor(
    trackingRepository: TrackingRepository,
    workScheduleRepository: WorkScheduleRepository,
    payPeriodSettingsRepository: PayPeriodSettingsRepository,
    workSiteSessionRepository: WorkSiteSessionRepository,
    private val clock: Clock,
    private val logger: AppLogger,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val refreshTicks = MutableStateFlow(0L)
    private val refreshing = MutableStateFlow(false)
    private val selectedWeekOffset = MutableStateFlow(
        savedStateHandle.get<Int>(SELECTED_WEEK_OFFSET_KEY)?.coerceAtMost(0) ?: 0,
    )
    private val historyRangeMode = MutableStateFlow(HistoryRangeMode.PAY_PERIOD)
    private val customStartDate = MutableStateFlow(LocalDate.now(clock).withDayOfMonth(1).toString())
    private val customEndDate = MutableStateFlow(LocalDate.now(clock).toString())
    private val siteFilter = MutableStateFlow("")
    private val historyControls: Flow<HistoryControls> = combine(
        historyRangeMode,
        customStartDate,
        customEndDate,
        siteFilter,
    ) { rangeMode, customStart, customEnd, filter ->
        HistoryControls(rangeMode, customStart, customEnd, filter)
    }

    private val settingsInputs: Flow<SettingsInputs> = combine(
        workScheduleRepository.observeWorkSchedule(),
        payPeriodSettingsRepository.observeSettings(),
    ) { schedule, payPeriod ->
        SettingsInputs(schedule = schedule, payPeriod = payPeriod)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dashboardInputs: Flow<ReportInputs> = combine(
        settingsInputs,
        selectedWeekOffset,
        refreshTicks,
    ) { settings, weekOffset, _ ->
        val today = LocalDate.now(clock)
        val instantRange = dashboardQueryRange(
            today = today,
            payPeriod = settings.payPeriod,
            selectedWeekOffset = weekOffset,
        ).toInstantQueryRange(clock.zone)
        ReportQuery(
            schedule = settings.schedule,
            payPeriod = settings.payPeriod,
            startInclusive = instantRange.startInclusive,
            endExclusive = instantRange.endExclusive,
        )
    }.flatMapLatest { query ->
        combine(
            trackingRepository.observeSessions(query.startInclusive, query.endExclusive),
            trackingRepository.observeActivityIntervals(query.startInclusive, query.endExclusive),
            workSiteSessionRepository.observeWorkSiteSessions(query.startInclusive, query.endExclusive),
        ) { sessions, intervals, workSiteSessions ->
            ReportInputs(
                sessions = sessions,
                intervals = intervals,
                workSiteSessions = workSiteSessions,
                schedule = query.schedule,
                payPeriod = query.payPeriod,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val historyInputs: Flow<ReportInputs> = combine(
        settingsInputs,
        historyControls,
        refreshTicks,
    ) { settings, history, _ ->
        val today = LocalDate.now(clock)
        val historySelection = resolveHistoryRange(
            mode = history.rangeMode,
            customStart = history.customStart,
            customEnd = history.customEnd,
            today = today,
            payPeriod = settings.payPeriod,
        )
        val instantRange = historySelection.range.toInstantQueryRange(clock.zone)
        ReportQuery(
            schedule = settings.schedule,
            payPeriod = settings.payPeriod,
            startInclusive = instantRange.startInclusive,
            endExclusive = instantRange.endExclusive,
        )
    }.flatMapLatest { query ->
        combine(
            trackingRepository.observeSessions(query.startInclusive, query.endExclusive),
            trackingRepository.observeActivityIntervals(query.startInclusive, query.endExclusive),
            workSiteSessionRepository.observeWorkSiteSessions(query.startInclusive, query.endExclusive),
        ) { sessions, intervals, workSiteSessions ->
            ReportInputs(
                sessions = sessions,
                intervals = intervals,
                workSiteSessions = workSiteSessions,
                schedule = query.schedule,
                payPeriod = query.payPeriod,
            )
        }
    }

    private val reportData: Flow<ReportData> = combine(dashboardInputs, historyInputs, ::ReportData)

    val uiState: StateFlow<ReportsUiState> = combine(
        reportData,
        refreshTicks,
        refreshing,
        historyControls,
        selectedWeekOffset,
    ) { data, _, isRefreshing, history, weekOffset ->
        val dashboard = data.dashboard
        val historyInputs = data.history
        val today = LocalDate.now(clock)
        val now = Instant.now(clock)
        val selectedWeekDate = today.plusWeeks(weekOffset.toLong())
        val selectedWeekStart = selectedWeekDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val calculator = ReportCalculator(clock.zone, dashboard.schedule, dashboard.payPeriod)
        val daily = calculator.daily(today, dashboard.sessions, dashboard.intervals, now, dashboard.workSiteSessions)
        val weekly = calculator.weekly(
            selectedWeekDate,
            dashboard.sessions,
            dashboard.intervals,
            now,
            dashboard.workSiteSessions,
        )
        val weeklyLedger = weekly.toWeeklyLedger(today, dashboard.schedule, now, clock.zone)
        val selectedWeekTitle = weeklyLedger.dateRange
        val payPeriodReport = calculator.payPeriod(
            today,
            dashboard.sessions,
            dashboard.intervals,
            now,
            dashboard.workSiteSessions,
        )
        val historySelection = resolveHistoryRange(
            mode = history.rangeMode,
            customStart = history.customStart,
            customEnd = history.customEnd,
            today = today,
            payPeriod = historyInputs.payPeriod,
        )
        val historyCalculator = ReportCalculator(clock.zone, historyInputs.schedule, historyInputs.payPeriod)
        val historyReport = historyCalculator.range(
            range = historySelection.range,
            sessions = historyInputs.sessions,
            intervals = historyInputs.intervals,
            now = now,
            workSiteSessions = historyInputs.workSiteSessions,
        )
        logger.info(
            LogCategory.REPORTS,
            "Reports recalculated",
            mapOf(
                "sessionCount" to dashboard.sessions.size,
                "activityIntervalCount" to dashboard.intervals.size,
                "workSiteSessionCount" to dashboard.workSiteSessions.size,
                "selectedWeekOffset" to weekOffset,
            ),
        )
        UiDebugState.setSelectedWeek(
            start = selectedWeekStart.toString(),
            end = selectedWeekStart.plusDays(6).toString(),
            mode = if (weekOffset == 0) "CURRENT_WEEK" else "SELECTED_WEEK",
        )
        ReportsUiState(
            isRefreshing = isRefreshing,
            summaries = if (weekOffset == 0) {
                listOf(
                    daily.toSummary("Today", now, clock.zone),
                    weekly.toSummary("Week", now, clock.zone),
                    payPeriodReport.toSummary("Pay Period", now, clock.zone),
                )
            } else {
                listOf(weekly.toSummary(selectedWeekTitle, now, clock.zone))
            },
            weeklyLedger = weeklyLedger.copy(
                title = if (weekOffset == 0) "This Week" else selectedWeekTitle,
            ),
            selectedWeekOffset = weekOffset,
            canNavigateToNextWeek = weekOffset < 0,
            selectedWeekStart = selectedWeekStart.toString(),
            selectedWeekEnd = selectedWeekStart.plusDays(6).toString(),
            summaryMode = if (weekOffset == 0) {
                DashboardSummaryMode.CURRENT_WEEK
            } else {
                DashboardSummaryMode.SELECTED_WEEK
            },
            historyRangeMode = history.rangeMode,
            customStartDate = history.customStart,
            customEndDate = history.customEnd,
            siteFilter = history.siteFilter,
            historyTitle = historySelection.title,
            historySummary = historyReport.toSummary("Selected Range", now, clock.zone),
            historyCalendar = historyReport.toHistoryCalendar(today, now, clock.zone),
            siteHistory = historyInputs.workSiteSessions.toSiteHistory(
                range = historySelection.range,
                now = now,
                zoneId = clock.zone,
                siteFilter = history.siteFilter,
                sessions = historyInputs.sessions,
                intervals = historyInputs.intervals,
            ),
            historyMessage = historySelection.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportsUiState(),
    )

    fun refreshDashboard() {
        viewModelScope.launch {
            refreshing.value = true
            refreshTicks.update { it + 1 }
            delay(REFRESH_INDICATOR_MILLIS)
            refreshing.value = false
        }
    }

    fun refreshForResume() {
        setSelectedWeekOffset(selectedWeekOffset.value.coerceAtMost(0))
        refreshTicks.update { it + 1 }
    }

    fun previousWeek() {
        setSelectedWeekOffset(selectedWeekOffset.value - 1)
    }

    fun nextWeek() {
        setSelectedWeekOffset((selectedWeekOffset.value + 1).coerceAtMost(0))
    }

    private fun setSelectedWeekOffset(offset: Int) {
        val guardedOffset = offset.coerceAtMost(0)
        selectedWeekOffset.value = guardedOffset
        savedStateHandle[SELECTED_WEEK_OFFSET_KEY] = guardedOffset
    }

    fun selectHistoryRange(mode: HistoryRangeMode) {
        historyRangeMode.value = mode
    }

    fun updateCustomStartDate(value: String) {
        customStartDate.value = value
    }

    fun updateCustomEndDate(value: String) {
        customEndDate.value = value
    }

    fun updateSiteFilter(value: String) {
        siteFilter.value = value
    }

    private companion object {
        const val SELECTED_WEEK_OFFSET_KEY = "selected_week_offset"
        const val REFRESH_INDICATOR_MILLIS = 250L
    }
}

private data class HistoryRangeSelection(val range: DateRange, val title: String, val message: String = "")

private data class HistoryControls(val rangeMode: HistoryRangeMode, val customStart: String, val customEnd: String, val siteFilter: String)

private data class SettingsInputs(val schedule: WorkSchedule, val payPeriod: PayPeriodSettings)

private data class ReportData(val dashboard: ReportInputs, val history: ReportInputs)

private data class ReportQuery(
    val schedule: WorkSchedule,
    val payPeriod: PayPeriodSettings,
    val startInclusive: Instant,
    val endExclusive: Instant,
)

private data class InstantQueryRange(val startInclusive: Instant, val endExclusive: Instant)

private fun resolveHistoryRange(
    mode: HistoryRangeMode,
    customStart: String,
    customEnd: String,
    today: LocalDate,
    payPeriod: PayPeriodSettings,
): HistoryRangeSelection {
    val month = YearMonth.from(today)
    return when (mode) {
        HistoryRangeMode.WEEK -> currentWeekRange(today).let { range ->
            HistoryRangeSelection(
                range = range,
                title = "Week: ${range.formatHistoryDateRange()}",
            )
        }
        HistoryRangeMode.PAY_PERIOD -> payPeriod.payPeriodRangeFor(today).let { range ->
            HistoryRangeSelection(
                range = range,
                title = "${payPeriod.cadence.label} Pay Period: ${range.formatHistoryDateRange()}",
            )
        }
        HistoryRangeMode.MONTH -> HistoryRangeSelection(
            range = DateRange(month.atDay(1), month.plusMonths(1).atDay(1)),
            title = month.toRangeTitle(),
        )
        HistoryRangeMode.YEAR -> HistoryRangeSelection(
            range = DateRange(
                today.withDayOfYear(1),
                today.withDayOfYear(1).plusYears(1),
            ),
            title = today.year.toString(),
        )
        HistoryRangeMode.CUSTOM -> customRange(customStart, customEnd, month)
    }
}

private fun customRange(customStart: String, customEnd: String, fallbackMonth: YearMonth): HistoryRangeSelection = try {
    val start = LocalDate.parse(customStart)
    val inclusiveEnd = LocalDate.parse(customEnd)
    val range = DateRange(start, inclusiveEnd.plusDays(1))
    HistoryRangeSelection(range = range, title = "$start to $inclusiveEnd")
} catch (_: IllegalArgumentException) {
    fallbackHistoryRange(fallbackMonth)
} catch (_: DateTimeParseException) {
    fallbackHistoryRange(fallbackMonth)
}

private fun fallbackHistoryRange(month: YearMonth): HistoryRangeSelection = HistoryRangeSelection(
    range = DateRange(month.atDay(1), month.plusMonths(1).atDay(1)),
    title = month.toRangeTitle(),
    message = "Enter custom dates as YYYY-MM-DD with the end after the start.",
)

private fun dashboardQueryRange(today: LocalDate, payPeriod: PayPeriodSettings, selectedWeekOffset: Int): DateRange {
    val selectedWeek = currentWeekRange(today.plusWeeks(selectedWeekOffset.toLong()))
    if (selectedWeekOffset < 0) return selectedWeek
    val ranges = listOf(DateRange(today, today.plusDays(1)), selectedWeek, payPeriod.payPeriodRangeFor(today))
    return DateRange(
        startInclusive = ranges.minOf(DateRange::startInclusive),
        endExclusive = ranges.maxOf(DateRange::endExclusive),
    )
}

private fun currentWeekRange(today: LocalDate): DateRange {
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return DateRange(start, start.plusWeeks(1))
}

private fun DateRange.toInstantQueryRange(zoneId: ZoneId): InstantQueryRange = InstantQueryRange(
    startInclusive = startInclusive.atStartOfDay(zoneId).toInstant(),
    endExclusive = endExclusive.atStartOfDay(zoneId).toInstant(),
)

private fun DateRange.formatHistoryDateRange(): String =
    "${startInclusive.format(HISTORY_TITLE_DATE_FORMATTER)} - ${endExclusive.minusDays(1).format(HISTORY_TITLE_DATE_FORMATTER)}"

private val HISTORY_TITLE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
