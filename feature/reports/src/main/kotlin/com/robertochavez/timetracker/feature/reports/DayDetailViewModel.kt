package com.robertochavez.timetracker.feature.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.DateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class DayDetailUiState(
    val date: String = "",
    val totals: WeeklyLedgerRowUiModel? = null,
    val worksites: List<WorksiteSummaryUiModel> = emptyList(),
    val sessions: List<AwaySession> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DayDetailViewModel @Inject constructor(source: ReportDataSource, clock: Clock, savedState: SavedStateHandle) : ViewModel() {
    private val date = LocalDate.parse(requireNotNull(savedState.get<String>("date")))
    private val range = DateRange(date, date.plusDays(1))
    private val refresh = MutableStateFlow(0)
    val uiState = refresh.flatMapLatest { source.observe(range) }.map { inputs ->
        DayDetailUiState(
            date = date.toString(),
            totals = inputs.calculate(
                range,
                clock,
            ).toWeeklyLedger(LocalDate.now(clock), inputs.schedule, clock.instant(), clock.zone).total,
            worksites = inputs.workSiteSessions.worksiteSummaries(range, clock.instant(), clock.zone),
            sessions = inputs.sessions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayDetailUiState(date.toString()))

    fun refresh() {
        refresh.value++
    }
}

@HiltViewModel
class WorksiteDetailViewModel @Inject constructor(source: ReportDataSource, clock: Clock, savedState: SavedStateHandle) : ViewModel() {
    private val id = requireNotNull(savedState.get<String>("worksiteId"))
    private val start = LocalDate.parse(requireNotNull(savedState.get<String>("date")))
    private val range = DateRange(start, start.plusWeeks(1))
    private val refresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState = refresh.flatMapLatest { source.observe(range) }.map { inputs ->
        inputs.workSiteSessions.worksiteSummaries(range, clock.instant(), clock.zone).firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        refresh.value++
    }
}
