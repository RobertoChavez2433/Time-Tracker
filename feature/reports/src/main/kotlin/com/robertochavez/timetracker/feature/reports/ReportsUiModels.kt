@file:Suppress("TooManyFunctions")

package com.robertochavez.timetracker.feature.reports

import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.model.WorkSiteSession

data class ReportsUiState(
    val weeklyLedger: WeeklyLedgerUiModel = WeeklyLedgerUiModel(),
    val selectedWeekOffset: Int = 0,
    val canNavigateToNextWeek: Boolean = false,
    val selectedWeekStart: String = "",
    val selectedWeekEnd: String = "",
    val weeklyWorksites: List<WorksiteSummaryUiModel> = emptyList(),
)

internal data class ReportInputs(
    val sessions: List<AwaySession>,
    val intervals: List<ActivityInterval>,
    val workSiteSessions: List<WorkSiteSession>,
    val schedule: WorkSchedule,
    val payPeriod: PayPeriodSettings,
)

data class DashboardSummaryUiModel(
    val title: String,
    val home: String,
    val work: String,
    val drive: String,
    val site: String,
    val idle: String,
    val miles: String,
) {
    companion object {
        fun empty(title: String): DashboardSummaryUiModel = DashboardSummaryUiModel(
            title = title,
            home = "-",
            work = "-",
            drive = "-",
            site = "-",
            idle = "-",
            miles = "-",
        )
    }
}

enum class HistoryRangeMode(val label: String) {
    WEEK("Week"),
    PAY_PERIOD("Pay Period"),
    MONTH("Month"),
    YEAR("Year"),
    CUSTOM("Custom"),
}

data class WeeklyLedgerUiModel(
    val title: String = "This Week",
    val dateRange: String = "",
    val rows: List<WeeklyLedgerRowUiModel> = emptyList(),
    val total: WeeklyLedgerRowUiModel = WeeklyLedgerRowUiModel(
        day = "Total",
        date = "",
        home = "-",
        work = "-",
        drive = "-",
        site = "-",
        idle = "-",
        miles = "-",
    ),
)

data class WeeklyLedgerRowUiModel(
    val day: String,
    val date: String,
    val home: String,
    val work: String,
    val drive: String,
    val site: String,
    val idle: String,
    val miles: String,
    val isToday: Boolean = false,
    val dateIso: String = "",
)

data class HistoryCalendarUiModel(
    val weekdayHeaders: List<String> = CALENDAR_WEEKDAY_HEADERS,
    val weeks: List<HistoryCalendarWeekUiModel> = emptyList(),
    val dailyRows: List<HistoryCalendarDayUiModel> = emptyList(),
)

data class HistoryCalendarWeekUiModel(val days: List<HistoryCalendarDayUiModel>)

data class HistoryCalendarDayUiModel(
    val dateIso: String,
    val dayNumber: String,
    val dayLabel: String,
    val home: String,
    val work: String,
    val drive: String,
    val miles: String,
    val isToday: Boolean,
    val isInSelectedRange: Boolean,
    val hasData: Boolean,
)
