package com.robertochavez.timetracker.feature.reports

import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.DailyReport
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.PeriodReport
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class ReportsUiState(
    val isRefreshing: Boolean = false,
    val summaries: List<DashboardSummaryUiModel> = emptyList(),
    val weeklyLedger: WeeklyLedgerUiModel = WeeklyLedgerUiModel(),
)

internal data class ReportInputs(
    val sessions: List<AwaySession>,
    val intervals: List<ActivityInterval>,
    val workSiteSessions: List<WorkSiteSession>,
    val schedule: WorkSchedule,
    val payPeriod: PayPeriodSettings,
)

data class DashboardSummaryUiModel(val title: String, val home: String, val work: String, val drive: String, val miles: String)

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
)

internal fun DailyReport.toSummary(title: String, now: Instant, zoneId: ZoneId): DashboardSummaryUiModel = DashboardSummaryUiModel(
    title = title,
    home = homeDuration(now, zoneId).formatHours(),
    work = totalAway.formatHours(),
    drive = drive.formatHours(),
    miles = drivenMiles.formatWholeMiles(),
)

internal fun PeriodReport.toSummary(title: String, now: Instant, zoneId: ZoneId): DashboardSummaryUiModel = DashboardSummaryUiModel(
    title = title,
    home = homeDuration(now, zoneId).formatHours(),
    work = totalAway.formatHours(),
    drive = drive.formatHours(),
    miles = drivenMiles.formatWholeMiles(),
)

internal fun PeriodReport.toWeeklyLedger(today: LocalDate, schedule: WorkSchedule, now: Instant, zoneId: ZoneId): WeeklyLedgerUiModel {
    val rows = dailyReports.map { report ->
        val isWorkday = schedule.isTrackable(report.date)
        val hasData = report.hasDashboardData()
        WeeklyLedgerRowUiModel(
            day = report.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US),
            date = report.date.format(DAY_DATE_FORMATTER),
            home = when {
                !isWorkday -> "Off"
                hasData -> report.homeDuration(now, zoneId).formatCompactHours()
                else -> "-"
            },
            work = report.totalAway.formatLedgerValue(hasData),
            drive = report.drive.formatLedgerValue(hasData),
            site = report.site.formatLedgerValue(hasData),
            idle = displayIdleDuration(report.totalAway, report.drive, report.site).formatLedgerValue(hasData),
            miles = report.drivenMiles.formatLedgerMiles(hasData),
            isToday = report.date == today,
        )
    }
    return WeeklyLedgerUiModel(
        dateRange = "${range.startInclusive.format(RANGE_DATE_FORMATTER)} - " +
            range.endExclusive.minusDays(1).format(RANGE_DATE_FORMATTER),
        rows = rows,
        total = WeeklyLedgerRowUiModel(
            day = "Total",
            date = "",
            home = homeDuration(now, zoneId).formatHours(),
            work = totalAway.formatHours(),
            drive = drive.formatHours(),
            site = site.formatHours(),
            idle = displayIdleDuration(totalAway, drive, site).formatHours(),
            miles = drivenMiles.formatWholeMiles(),
        ),
    )
}

private fun PeriodReport.homeDuration(now: Instant, zoneId: ZoneId): Duration = dailyReports
    .filter(DailyReport::hasDashboardData)
    .fold(Duration.ZERO) { total, report -> total.plus(report.homeDuration(now, zoneId)) }

private fun DailyReport.homeDuration(now: Instant, zoneId: ZoneId): Duration {
    val currentDate = now.atZone(zoneId).toLocalDate()
    if (date.isAfter(currentDate)) return Duration.ZERO

    val dayStart = date.atStartOfDay(zoneId).toInstant()
    val dayEnd = if (date == currentDate) {
        now
    } else {
        date.plusDays(1).atStartOfDay(zoneId).toInstant()
    }
    return Duration.between(dayStart, dayEnd).minus(totalAway).coerceNonNegative()
}

private fun DailyReport.hasDashboardData(): Boolean = !totalAway.isZero || drivenMiles > 0.0

private fun Duration.coerceNonNegative(): Duration = if (isNegative) Duration.ZERO else this

private fun displayIdleDuration(work: Duration, drive: Duration, site: Duration): Duration {
    val visibleIdleMinutes = work.toMinutes() - drive.toMinutes() - site.toMinutes()
    return Duration.ofMinutes(visibleIdleMinutes.coerceAtLeast(0))
}

private fun Duration.formatHours(): String {
    val totalMinutes = toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalMinutes == 0L -> "0m"
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

private fun Duration.formatCompactHours(): String {
    val totalMinutes = toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalMinutes == 0L -> "0m"
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h${minutes.toString().padStart(2, '0')}"
    }
}

private fun Duration.formatLedgerValue(hasData: Boolean): String = if (hasData) formatCompactHours() else "-"

private fun Double.formatWholeMiles(): String = roundToInt().toString()

private fun Double.formatLedgerMiles(hasData: Boolean): String = if (hasData) formatWholeMiles() else "-"

private val DAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d", Locale.US)

private val RANGE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
