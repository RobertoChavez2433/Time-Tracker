package com.robertochavez.timetracker.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.domain.ReportCalculator
import com.robertochavez.timetracker.core.common.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.common.repository.WorkSiteSessionRepository
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    trackingRepository: TrackingRepository,
    workScheduleRepository: WorkScheduleRepository,
    payPeriodSettingsRepository: PayPeriodSettingsRepository,
    workSiteSessionRepository: WorkSiteSessionRepository,
    private val clock: Clock,
    private val logger: AppLogger,
) : ViewModel() {
    private val refreshTicks = MutableStateFlow(0L)
    private val refreshing = MutableStateFlow(false)
    private val reportInputs: Flow<ReportInputs> = combine(
        trackingRepository.observeSessions(),
        trackingRepository.observeActivityIntervals(),
        workSiteSessionRepository.observeWorkSiteSessions(),
        workScheduleRepository.observeWorkSchedule(),
        payPeriodSettingsRepository.observeSettings(),
    ) { sessions, intervals, workSiteSessions, schedule, payPeriod ->
        ReportInputs(sessions, intervals, workSiteSessions, schedule, payPeriod)
    }

    val uiState: StateFlow<ReportsUiState> = combine(
        reportInputs,
        refreshTicks,
        refreshing,
    ) { inputs, _, isRefreshing ->
        val today = LocalDate.now(clock)
        val now = Instant.now(clock)
        val calculator = ReportCalculator(clock.zone, inputs.schedule, inputs.payPeriod)
        val daily = calculator.daily(today, inputs.sessions, inputs.intervals, now, inputs.workSiteSessions)
        val weekly = calculator.weekly(today, inputs.sessions, inputs.intervals, now, inputs.workSiteSessions)
        val biweekly = calculator.biweekly(today, inputs.sessions, inputs.intervals, now, inputs.workSiteSessions)
        logger.info(
            LogCategory.REPORTS,
            "Reports recalculated",
            mapOf(
                "sessionCount" to inputs.sessions.size,
                "activityIntervalCount" to inputs.intervals.size,
                "workSiteSessionCount" to inputs.workSiteSessions.size,
            ),
        )
        ReportsUiState(
            isRefreshing = isRefreshing,
            summaries = listOf(
                daily.toSummary("Today", now, clock.zone),
                weekly.toSummary("Week", now, clock.zone),
                biweekly.toSummary("Pay Period", now, clock.zone),
            ),
            weeklyLedger = weekly.toWeeklyLedger(today, inputs.schedule, now, clock.zone),
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
        refreshTicks.update { it + 1 }
    }

    private companion object {
        const val REFRESH_INDICATOR_MILLIS = 250L
    }
}
