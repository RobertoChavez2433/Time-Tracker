package com.robertochavez.timetracker.feature.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.model.UiDebugState
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
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    source: ReportDataSource,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val offset = savedStateHandle.getStateFlow("selected_week_offset", 0)
    private val refresh = MutableStateFlow(0)
    val uiState = combine(offset, refresh) { offset, _ -> offset.coerceAtMost(0) }
        .flatMapLatest { offset ->
            val range = weekRange(LocalDate.now(clock).plusWeeks(offset.toLong()))
            source.observe(range).map { inputs ->
                val ledger = inputs.calculate(
                    range,
                    clock,
                ).toWeeklyLedger(LocalDate.now(clock), inputs.schedule, clock.instant(), clock.zone)
                UiDebugState.setSelectedWeek(
                    range.startInclusive.toString(),
                    range.endExclusive.minusDays(1).toString(),
                    if (offset == 0) "CURRENT_WEEK" else "SELECTED_WEEK",
                )
                ReportsUiState(
                    weeklyLedger = ledger.copy(title = if (offset == 0) "This Week" else "Selected Week"),
                    selectedWeekOffset = offset,
                    canNavigateToNextWeek = offset < 0,
                    selectedWeekStart = range.startInclusive.toString(),
                    selectedWeekEnd = range.endExclusive.minusDays(1).toString(),
                    weeklyWorksites = inputs.workSiteSessions.worksiteSummaries(range, clock.instant(), clock.zone),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun previousWeek() {
        savedStateHandle["selected_week_offset"] = offset.value.coerceAtMost(0) - 1
    }
    fun nextWeek() {
        savedStateHandle["selected_week_offset"] = (offset.value + 1).coerceAtMost(0)
    }
    fun refreshForResume() {
        refresh.value++
    }
    fun refreshDashboard() = refreshForResume()
}
