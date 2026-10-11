@file:Suppress("TooManyFunctions")

package com.robertochavez.timetracker.feature.reports

import com.robertochavez.timetracker.core.common.model.DailyReport
import com.robertochavez.timetracker.core.common.model.PeriodReport
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.util.coerceNonNegative
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

internal fun DailyReport.toSummary(title: String, now: Instant, zoneId: ZoneId): DashboardSummaryUiModel = DashboardSummaryUiModel(
    title = title,
    home = homeDuration(now, zoneId).formatHours(),
    work = totalAway.formatHours(),
    drive = drive.formatHours(),
    site = site.formatHours(),
    idle = idle.formatHours(),
    miles = drivenMiles.formatWholeMiles(),
)

internal fun PeriodReport.toSummary(title: String, now: Instant, zoneId: ZoneId): DashboardSummaryUiModel = DashboardSummaryUiModel(
    title = title,
    home = homeDuration(now, zoneId).formatHours(),
    work = totalAway.formatHours(),
    drive = drive.formatHours(),
    site = site.formatHours(),
    idle = idle.formatHours(),
    miles = drivenMiles.formatWholeMiles(),
)

internal fun YearMonth.toRangeTitle(): String = format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))

internal fun PeriodReport.toHistoryCalendar(
    today: LocalDate,
    now: Instant,
    zoneId: ZoneId,
    month: YearMonth = YearMonth.from(range.startInclusive),
): HistoryCalendarUiModel {
    val reportsByDate = dailyReports.associateBy(DailyReport::date)
    val firstGridDate = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastGridDate = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val gridDays = generateSequence(firstGridDate) { it.plusDays(1) }
        .takeWhile { !it.isAfter(lastGridDate) }
        .map { date ->
            reportsByDate[date]?.takeIf { YearMonth.from(date) == month }?.toHistoryCalendarDay(today, now, zoneId)
                ?: blankHistoryCalendarDay(date, today)
        }
        .toList()
    return HistoryCalendarUiModel(
        weeks = gridDays.chunked(DAYS_IN_WEEK).map(::HistoryCalendarWeekUiModel),
        dailyRows = dailyReports
            .map { report -> report.toHistoryCalendarDay(today, now, zoneId) }
            .filter(HistoryCalendarDayUiModel::hasData)
            .sortedByDescending(HistoryCalendarDayUiModel::dateIso),
    )
}

internal fun PeriodReport.toWeeklyLedger(today: LocalDate, schedule: WorkSchedule, now: Instant, zoneId: ZoneId): WeeklyLedgerUiModel {
    val rows = dailyReports.map { report ->
        val isWorkday = schedule.isTrackable(report.date)
        val hasData = report.hasDashboardData()
        WeeklyLedgerRowUiModel(
            dateIso = report.date.toString(),
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
            idle = report.idle.formatLedgerValue(hasData),
            miles = report.drivenMiles.formatLedgerMiles(hasData),
            isToday = report.date == today,
        )
    }
    val weekEnd = range.endExclusive.minusDays(1)
    return WeeklyLedgerUiModel(
        dateRange = if (range.startInclusive.year == weekEnd.year) {
            "${range.startInclusive.format(RANGE_DATE_FORMATTER)} - ${weekEnd.format(RANGE_DATE_WITH_YEAR_FORMATTER)}"
        } else {
            "${range.startInclusive.format(RANGE_DATE_WITH_YEAR_FORMATTER)} - ${weekEnd.format(RANGE_DATE_WITH_YEAR_FORMATTER)}"
        },
        rows = rows,
        total = WeeklyLedgerRowUiModel(
            day = "Total",
            date = "",
            home = homeDuration(now, zoneId).formatHours(),
            work = totalAway.formatHours(),
            drive = drive.formatHours(),
            site = site.formatHours(),
            idle = idle.formatHours(),
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

internal fun Duration.formatHours(): String {
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
private val RANGE_DATE_WITH_YEAR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

private val CALENDAR_DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

private const val DAYS_IN_WEEK = 7

internal val CALENDAR_WEEKDAY_HEADERS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun DailyReport.toHistoryCalendarDay(today: LocalDate, now: Instant, zoneId: ZoneId): HistoryCalendarDayUiModel =
    HistoryCalendarDayUiModel(
        dateIso = date.toString(),
        dayNumber = date.dayOfMonth.toString(),
        dayLabel = date.format(CALENDAR_DAY_FORMATTER),
        home = homeDuration(now, zoneId).formatCompactHours(),
        work = totalAway.formatCompactHours(),
        drive = drive.formatCompactHours(),
        miles = drivenMiles.formatWholeMiles(),
        isToday = date == today,
        isInSelectedRange = true,
        hasData = hasDashboardData(),
    )

private fun blankHistoryCalendarDay(date: LocalDate, today: LocalDate): HistoryCalendarDayUiModel = HistoryCalendarDayUiModel(
    dateIso = date.toString(),
    dayNumber = date.dayOfMonth.toString(),
    dayLabel = date.format(CALENDAR_DAY_FORMATTER),
    home = "-",
    work = "-",
    drive = "-",
    miles = "-",
    isToday = date == today,
    isInSelectedRange = false,
    hasData = false,
)
