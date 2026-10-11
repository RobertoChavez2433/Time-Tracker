package com.robertochavez.timetracker.feature.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.domain.payPeriodRangeFor
import com.robertochavez.timetracker.core.common.model.DateRange
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.repository.PayPeriodSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class HistoryUiState(
    val mode: HistoryRangeMode = HistoryRangeMode.MONTH,
    val month: String = "",
    val start: String = "",
    val end: String = "",
    val title: String = "",
    val calendar: HistoryCalendarUiModel = HistoryCalendarUiModel(),
    val summary: DashboardSummaryUiModel = DashboardSummaryUiModel.empty("Selected range"),
    val error: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    source: ReportDataSource,
    payPeriods: PayPeriodSettingsRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val month = savedState.getStateFlow("month", YearMonth.now(clock).toString())
    private val mode = savedState.getStateFlow("mode", HistoryRangeMode.MONTH.name)

    // Text fields must reflect keystrokes immediately, independently of database report queries.
    val startInput = savedState.getStateFlow("start", LocalDate.now(clock).withDayOfMonth(1).toString())
    val endInput = savedState.getStateFlow("end", LocalDate.now(clock).toString())
    private val refresh = MutableStateFlow(0)
    private val controls = combine(month, mode, startInput, endInput) { month, mode, start, end ->
        HistoryControls(YearMonth.parse(month), HistoryRangeMode.valueOf(mode), start, end)
    }
    val uiState = combine(controls, payPeriods.observeSettings(), refresh) { controls, periods, _ ->
        controls to controls.selection(periods, LocalDate.now(clock))
    }.flatMapLatest { (controls, selection) ->
        source.observe(selection.range).map { inputs ->
            val report = inputs.calculate(selection.range, clock)
            HistoryUiState(
                mode = controls.mode,
                month = controls.month.toString(),
                start = controls.start,
                end = controls.end,
                title = "${selection.range.startInclusive} – ${selection.range.endExclusive.minusDays(1)}",
                calendar = report.toHistoryCalendar(LocalDate.now(clock), clock.instant(), clock.zone, controls.month),
                summary = report.toSummary("Selected range", clock.instant(), clock.zone),
                error = selection.error,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun previousMonth() {
        savedState["month"] = YearMonth.parse(month.value).minusMonths(1).toString()
    }
    fun nextMonth() {
        savedState["month"] = YearMonth.parse(month.value).plusMonths(1).toString()
    }
    fun selectRange(value: HistoryRangeMode) {
        savedState["mode"] = value.name
    }
    fun updateStart(value: String) {
        savedState["start"] = value
    }
    fun updateEnd(value: String) {
        savedState["end"] = value
    }
    fun refresh() {
        refresh.value++
    }
}

private data class HistorySelection(val range: DateRange, val error: String = "")
private data class HistoryControls(val month: YearMonth, val mode: HistoryRangeMode, val start: String, val end: String) {
    fun selection(periods: PayPeriodSettings, today: LocalDate): HistorySelection {
        val anchor = if (YearMonth.from(today) == month) today else month.atDay(1)
        val monthRange = DateRange(month.atDay(1), month.plusMonths(1).atDay(1))
        return when (mode) {
            HistoryRangeMode.WEEK -> HistorySelection(weekRange(anchor))
            HistoryRangeMode.PAY_PERIOD -> HistorySelection(periods.payPeriodRangeFor(anchor))
            HistoryRangeMode.MONTH -> HistorySelection(monthRange)
            HistoryRangeMode.YEAR -> HistorySelection(
                DateRange(month.atDay(1).withDayOfYear(1), month.atDay(1).withDayOfYear(1).plusYears(1)),
            )
            HistoryRangeMode.CUSTOM -> runCatching {
                HistorySelection(DateRange(LocalDate.parse(start), LocalDate.parse(end).plusDays(1)))
            }.getOrElse { HistorySelection(monthRange, "Enter dates as YYYY-MM-DD. End must be on or after start.") }
        }
    }
}
