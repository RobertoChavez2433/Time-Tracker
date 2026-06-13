@file:Suppress("TooManyFunctions")

package com.robertochavez.timetracker.feature.reports

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.DailyReport
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.PeriodReport
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
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

data class ReportsUiState(
    val isRefreshing: Boolean = false,
    val summaries: List<DashboardSummaryUiModel> = emptyList(),
    val weeklyLedger: WeeklyLedgerUiModel = WeeklyLedgerUiModel(),
    val historyRangeMode: HistoryRangeMode = HistoryRangeMode.PAY_PERIOD,
    val customStartDate: String = "",
    val customEndDate: String = "",
    val siteFilter: String = "",
    val historyTitle: String = "",
    val historySummary: DashboardSummaryUiModel = DashboardSummaryUiModel.empty("History"),
    val historyCalendar: HistoryCalendarUiModel = HistoryCalendarUiModel(),
    val siteHistory: List<SiteHistoryGroupUiModel> = emptyList(),
    val historyMessage: String = "",
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
    val needsReview: String,
    val miles: String,
) {
    companion object {
        fun empty(title: String): DashboardSummaryUiModel = DashboardSummaryUiModel(
            title = title,
            home = "-",
            work = "-",
            drive = "-",
            needsReview = "-",
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
        needsReview = "-",
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
    val needsReview: String,
    val miles: String,
    val isToday: Boolean = false,
)

data class SiteHistoryGroupUiModel(
    val label: String,
    val total: String,
    val visitCount: String,
    val lastVisit: String,
    val drive: String,
    val miles: String,
    val dailyRows: List<SiteHistoryDayUiModel>,
)

data class SiteHistoryDayUiModel(val date: String, val total: String, val visits: String)

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

internal fun DailyReport.toSummary(title: String, now: Instant, zoneId: ZoneId): DashboardSummaryUiModel = DashboardSummaryUiModel(
    title = title,
    home = homeDuration(now, zoneId).formatHours(),
    work = totalAway.formatHours(),
    drive = drive.formatHours(),
    needsReview = needsReview.formatHours(),
    miles = drivenMiles.formatWholeMiles(),
)

internal fun PeriodReport.toSummary(title: String, now: Instant, zoneId: ZoneId): DashboardSummaryUiModel = DashboardSummaryUiModel(
    title = title,
    home = homeDuration(now, zoneId).formatHours(),
    work = totalAway.formatHours(),
    drive = drive.formatHours(),
    needsReview = needsReview.formatHours(),
    miles = drivenMiles.formatWholeMiles(),
)

internal fun YearMonth.toRangeTitle(): String = format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))

internal fun PeriodReport.toHistoryCalendar(today: LocalDate, now: Instant, zoneId: ZoneId): HistoryCalendarUiModel {
    val reportsByDate = dailyReports.associateBy(DailyReport::date)
    val firstGridDate = range.startInclusive.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastGridDate = range.endExclusive.minusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val gridDays = generateSequence(firstGridDate) { it.plusDays(1) }
        .takeWhile { !it.isAfter(lastGridDate) }
        .map { date ->
            reportsByDate[date]?.toHistoryCalendarDay(today, now, zoneId)
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

internal fun List<WorkSiteSession>.toSiteHistory(
    range: com.robertochavez.timetracker.core.common.model.DateRange,
    now: Instant,
    zoneId: ZoneId,
    siteFilter: String,
    sessions: List<AwaySession>,
    intervals: List<ActivityInterval>,
): List<SiteHistoryGroupUiModel> {
    val normalizedFilter = siteFilter.trim()
    val historyRange = range.toInstantRange(zoneId)
    return flatMap { session -> session.toSiteSlices(range, now, zoneId) }
        .filter { slice -> normalizedFilter.isBlank() || slice.label.contains(normalizedFilter, ignoreCase = true) }
        .groupBy(SiteVisitSlice::label)
        .map { (label, slices) ->
            val visitIds = slices.map(SiteVisitSlice::visitId).toSet()
            val totalDuration = slices.fold(Duration.ZERO) { total, slice -> total.plus(slice.duration) }
            val lastVisit = slices.maxByOrNull(SiteVisitSlice::end)
            val tripContext = siteTripContext(
                slices = slices,
                sessions = sessions,
                intervals = intervals,
                historyRange = historyRange,
                now = now,
            )
            SiteHistoryGroupUiModel(
                label = label,
                total = totalDuration.formatHours(),
                visitCount = "${visitIds.size}",
                lastVisit = lastVisit?.end?.atZone(zoneId)?.format(LAST_VISIT_FORMATTER).orEmpty(),
                drive = tripContext.drive.formatHours(),
                miles = tripContext.miles.formatWholeMiles(),
                dailyRows = slices.groupBy(SiteVisitSlice::date)
                    .entries
                    .sortedByDescending { it.key }
                    .map { (date, daySlices) ->
                        SiteHistoryDayUiModel(
                            date = date.format(DAY_HISTORY_FORMATTER),
                            total = daySlices.fold(Duration.ZERO) { total, slice -> total.plus(slice.duration) }.formatHours(),
                            visits = daySlices.map(SiteVisitSlice::visitId).toSet().size.toString(),
                        )
                    },
            )
        }
        .sortedByDescending { group ->
            slicesLastEpochMillis(group, this, range, now, zoneId)
        }
}

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
            idle = report.idle.formatLedgerValue(hasData),
            needsReview = report.needsReview.formatLedgerValue(hasData),
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
            idle = idle.formatHours(),
            needsReview = needsReview.formatHours(),
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

private val DAY_HISTORY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

private val CALENDAR_DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

private val LAST_VISIT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

private const val DAYS_IN_WEEK = 7

private val CALENDAR_WEEKDAY_HEADERS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private data class SiteVisitSlice(val visitId: String, val label: String, val date: LocalDate, val start: Instant, val end: Instant) {
    val duration: Duration = Duration.between(start, end)
}

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

private data class SiteTripContext(val drive: Duration, val miles: Double)

private data class SiteInstantRange(val start: Instant, val end: Instant) {
    init {
        require(end > start) { "Site instant range end must be after start." }
    }

    val duration: Duration = Duration.between(start, end)

    fun intersects(other: SiteInstantRange): Boolean = start < other.end && end > other.start

    fun overlapDuration(other: SiteInstantRange): Duration {
        val overlapStart = maxOf(start, other.start)
        val overlapEnd = minOf(end, other.end)
        return if (overlapEnd.isAfter(overlapStart)) Duration.between(overlapStart, overlapEnd) else Duration.ZERO
    }
}

private fun WorkSiteSession.toSiteSlices(
    range: com.robertochavez.timetracker.core.common.model.DateRange,
    now: Instant,
    zoneId: ZoneId,
): List<SiteVisitSlice> {
    val effectiveEnd = end ?: now
    if (!effectiveEnd.isAfter(start)) return emptyList()
    val startDate = start.atZone(zoneId).toLocalDate()
    val endDate = effectiveEnd.minusMillis(1).atZone(zoneId).toLocalDate()
    return generateSequence(startDate) { it.plusDays(1) }
        .takeWhile { !it.isAfter(endDate) }
        .filter(range::contains)
        .mapNotNull { date ->
            val dayStart = date.atStartOfDay(zoneId).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            val sliceStart = maxOf(start, dayStart)
            val sliceEnd = minOf(effectiveEnd, dayEnd)
            if (sliceEnd.isAfter(sliceStart)) {
                SiteVisitSlice(
                    visitId = id,
                    label = workLocationLabelSnapshot,
                    date = date,
                    start = sliceStart,
                    end = sliceEnd,
                )
            } else {
                null
            }
        }
        .toList()
}

private fun siteTripContext(
    slices: List<SiteVisitSlice>,
    sessions: List<AwaySession>,
    intervals: List<ActivityInterval>,
    historyRange: SiteInstantRange,
    now: Instant,
): SiteTripContext {
    val siteRanges = slices.map { SiteInstantRange(it.start, it.end) }
    val associatedSessions = sessions
        .filter(AwaySession::countsTowardTotals)
        .filter { session -> session.overlapsAny(siteRanges, now) }
    val associatedSessionIds = associatedSessions.map(AwaySession::id).toSet()
    val driveDuration = intervals
        .filter { interval -> interval.sessionId in associatedSessionIds && interval.bucket == ActivityBucket.DRIVE }
        .fold(Duration.ZERO) { total, interval -> total.plus(interval.toRange().overlapDuration(historyRange)) }
    val miles = associatedSessions.sumOf { session -> session.drivenMilesIn(historyRange, now) }
    return SiteTripContext(drive = driveDuration, miles = miles)
}

private fun AwaySession.overlapsAny(ranges: List<SiteInstantRange>, now: Instant): Boolean {
    val sessionRange = toRange(now) ?: return false
    return ranges.any(sessionRange::intersects)
}

private fun AwaySession.drivenMilesIn(range: SiteInstantRange, now: Instant): Double {
    val sessionRange = toRange(now)
    return if (drivenMiles <= 0.0 || sessionRange == null) {
        0.0
    } else {
        val overlap = sessionRange.overlapDuration(range)
        if (overlap.isZero || sessionRange.duration.isZero) {
            0.0
        } else {
            drivenMiles * overlap.toMillis().toDouble() / sessionRange.duration.toMillis().toDouble()
        }
    }
}

private fun AwaySession.toRange(now: Instant): SiteInstantRange? {
    val effectiveEnd = end ?: now
    return if (effectiveEnd.isAfter(start)) SiteInstantRange(start, effectiveEnd) else null
}

private fun ActivityInterval.toRange(): SiteInstantRange = SiteInstantRange(start, end)

private fun com.robertochavez.timetracker.core.common.model.DateRange.toInstantRange(zoneId: ZoneId): SiteInstantRange = SiteInstantRange(
    start = startInclusive.atStartOfDay(zoneId).toInstant(),
    end = endExclusive.atStartOfDay(zoneId).toInstant(),
)

private fun slicesLastEpochMillis(
    group: SiteHistoryGroupUiModel,
    sessions: List<WorkSiteSession>,
    range: com.robertochavez.timetracker.core.common.model.DateRange,
    now: Instant,
    zoneId: ZoneId,
): Long = sessions.flatMap { it.toSiteSlices(range, now, zoneId) }
    .filter { it.label == group.label }
    .maxOfOrNull { it.end.toEpochMilli() } ?: Long.MIN_VALUE
