package com.robertochavez.timetracker.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.domain.ReportCalculator
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.DailyReport
import com.robertochavez.timetracker.core.common.model.PeriodReport
import com.robertochavez.timetracker.core.database.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.database.repository.TrackingRepository
import com.robertochavez.timetracker.core.database.repository.WorkScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    trackingRepository: TrackingRepository,
    workScheduleRepository: WorkScheduleRepository,
    payPeriodSettingsRepository: PayPeriodSettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    val uiState: StateFlow<ReportsUiState> = combine(
        trackingRepository.observeSessions(),
        trackingRepository.observeActivityIntervals(),
        workScheduleRepository.observeWorkSchedule(),
        payPeriodSettingsRepository.observeSettings(),
    ) { sessions, intervals, schedule, payPeriod ->
        val today = LocalDate.now(clock)
        val now = Instant.now(clock)
        val calculator = ReportCalculator(clock.zone, schedule, payPeriod)
        val daily = calculator.daily(today, sessions, intervals, now)
        val weekly = calculator.weekly(today, sessions, intervals, now)
        val biweekly = calculator.biweekly(today, sessions, intervals, now)
        val monthly = calculator.monthly(YearMonth.from(today), sessions, intervals, now)
        val yearly = calculator.yearly(today.year, sessions, intervals, now)
        ReportsUiState(
            reports = listOf(
                daily.toUi("Today"),
                weekly.toUi("This week"),
                biweekly.toUi("Current biweekly period"),
                monthly.toUi("This month"),
                yearly.toUi("This year"),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportsUiState(),
    )
}

data class ReportsUiState(
    val reports: List<ReportUiModel> = emptyList(),
)

data class ReportUiModel(
    val title: String,
    val away: String,
    val miles: String,
    val drive: String,
    val idle: String,
    val unclassified: String,
)

private fun DailyReport.toUi(title: String): ReportUiModel = ReportUiModel(
    title = title,
    away = totalAway.formatHours(),
    miles = drivenMiles.formatMiles(),
    drive = drive.formatHours(),
    idle = idle.formatHours(),
    unclassified = unclassified.formatHours(),
)

private fun PeriodReport.toUi(title: String): ReportUiModel = ReportUiModel(
    title = title,
    away = totalAway.formatHours(),
    miles = drivenMiles.formatMiles(),
    drive = bucketTotals.getValue(ActivityBucket.DRIVE).formatHours(),
    idle = bucketTotals.getValue(ActivityBucket.IDLE).formatHours(),
    unclassified = bucketTotals.getValue(ActivityBucket.UNCLASSIFIED).formatHours(),
)

private fun Duration.formatHours(): String {
    val totalMinutes = toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours}h ${minutes}m"
}

private fun Double.formatMiles(): String = "%.1f mi".format(this)
