package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.DailyReport
import com.robertochavez.timetracker.core.common.model.DateRange
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.PeriodReport
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class ReportCalculator(
    private val zoneId: ZoneId,
    private val workSchedule: WorkSchedule,
    private val payPeriodSettings: PayPeriodSettings,
) {
    fun daily(
        date: LocalDate,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
        workSiteSessions: List<WorkSiteSession> = emptyList(),
    ): DailyReport {
        if (!workSchedule.isTrackable(date)) {
            return DailyReport(date, Duration.ZERO, zeroBucketTotals(), drivenMiles = 0.0)
        }
        return dailyReport(
            date = date,
            evidence = ReportEvidence(sessions, intervals, workSiteSessions),
            now = now,
            zoneId = zoneId,
        )
    }

    fun weekly(
        dateInWeek: LocalDate,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
        workSiteSessions: List<WorkSiteSession> = emptyList(),
    ): PeriodReport {
        val weekStart = dateInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return period(DateRange(weekStart, weekStart.plusWeeks(1)), sessions, intervals, now, workSiteSessions)
    }

    fun biweekly(
        dateInPeriod: LocalDate,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
        workSiteSessions: List<WorkSiteSession> = emptyList(),
    ): PeriodReport = period(
        payPeriodSettings.biweeklyRangeFor(dateInPeriod),
        sessions,
        intervals,
        now,
        workSiteSessions,
    )

    fun monthly(
        month: YearMonth,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
        workSiteSessions: List<WorkSiteSession> = emptyList(),
    ): PeriodReport {
        val start = month.atDay(1)
        return period(DateRange(start, month.plusMonths(1).atDay(1)), sessions, intervals, now, workSiteSessions)
    }

    fun yearly(
        year: Int,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
        workSiteSessions: List<WorkSiteSession> = emptyList(),
    ): PeriodReport {
        val start = LocalDate.of(year, 1, 1)
        return period(DateRange(start, start.plusYears(1)), sessions, intervals, now, workSiteSessions)
    }

    fun range(
        range: DateRange,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
        workSiteSessions: List<WorkSiteSession> = emptyList(),
    ): PeriodReport = period(range, sessions, intervals, now, workSiteSessions)

    private fun period(
        range: DateRange,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
        workSiteSessions: List<WorkSiteSession>,
    ): PeriodReport {
        val dailyReports = generateSequence(range.startInclusive) { it.plusDays(1) }
            .takeWhile { it.isBefore(range.endExclusive) }
            .map { daily(it, sessions, intervals, now, workSiteSessions) }
            .toList()

        val totalAway = dailyReports.fold(Duration.ZERO) { total, report -> total.plus(report.totalAway) }
        val bucketTotals = ActivityBucket.entries.associateWith { bucket ->
            dailyReports.fold(Duration.ZERO) { total, report -> total.plus(report.bucketTotals[bucket] ?: Duration.ZERO) }
        }

        return PeriodReport(
            range = range,
            totalAway = totalAway,
            bucketTotals = bucketTotals,
            drivenMiles = dailyReports.sumOf { it.drivenMiles },
            dailyReports = dailyReports,
        )
    }
}

private data class ReportEvidence(
    val sessions: List<AwaySession>,
    val intervals: List<ActivityInterval>,
    val workSiteSessions: List<WorkSiteSession>,
)

private fun dailyReport(date: LocalDate, evidence: ReportEvidence, now: Instant, zoneId: ZoneId): DailyReport {
    val sessions = evidence.sessions
    val intervals = evidence.intervals
    val range = DateRange(date, date.plusDays(1))
    val dateRange = date.instantRange(zoneId)
    val countedSessions = sessions.filter { it.countsTowardTotals }
    val countedSessionIds = countedSessions.map { it.id }.toSet()
    val awaySlices = countedSessions
        .flatMap { SessionSplitter.splitByDay(it, zoneId, now) }
        .filter { it.date == date }
    val awayRanges = awaySlices.map { InstantRange(it.start, it.end) }.merge()
    val homeRanges = intervals
        .filter { it.sessionId in countedSessionIds && it.bucket == ActivityBucket.HOME }
        .map { InstantRange(it.start, it.end) }
        .constrainTo(listOf(dateRange))
        .merge()
    val workRanges = awayRanges.subtract(homeRanges).merge()
    val siteRanges = (
        evidence.workSiteSessions.mapNotNull { it.toRange(now) } +
            intervals
                .filter { it.sessionId in countedSessionIds && it.bucket == ActivityBucket.SITE }
                .map { InstantRange(it.start, it.end) }
        )
        .constrainTo(listOf(dateRange))
        .merge()
    val siteWorkRanges = siteRanges.constrainTo(workRanges).merge()
    val siteTotal = siteWorkRanges.totalDuration()
    val intervalDriveRanges = intervals
        .filter { it.sessionId in countedSessionIds && it.bucket == ActivityBucket.DRIVE }
        .flatMap { SessionSplitter.splitActivityIntervalByDay(it, zoneId) }
        .filter { range.contains(it.date) }
        .map { InstantRange(it.start, it.end) }
        .merge()
    val driveRanges = intervalDriveRanges
        .constrainTo(workRanges)
        .subtract(siteWorkRanges)
        .merge()
    val driveTotal = driveRanges.totalDuration()
    val idleRanges = intervals
        .filter { it.sessionId in countedSessionIds && it.bucket == ActivityBucket.IDLE }
        .flatMap { SessionSplitter.splitActivityIntervalByDay(it, zoneId) }
        .filter { range.contains(it.date) }
        .map { InstantRange(it.start, it.end) }
        .merge()
        .constrainTo(workRanges)
        .subtract(siteWorkRanges)
        .subtract(driveRanges)
        .merge()
    val idleTotal = idleRanges.totalDuration()
    val awayTotal = workRanges.totalDuration()
    val knownRanges = (siteWorkRanges + driveRanges + idleRanges).merge()
    val needsReviewRanges = workRanges
        .subtract(knownRanges)
        .merge()
    val needsReviewTotal = needsReviewRanges.totalDuration()
    val homeTotal = awayRanges.overlapDuration(homeRanges)
    val bucketTotals = normalizedBucketTotals(
        home = homeTotal,
        drive = driveTotal,
        site = siteTotal,
        idle = idleTotal,
        needsReview = needsReviewTotal,
    )

    return DailyReport(
        date = date,
        totalAway = awayTotal,
        bucketTotals = bucketTotals,
        drivenMiles = drivenMilesForDate(date, sessions, intervals, now, zoneId),
        drive = driveTotal,
        site = siteTotal,
        idle = idleTotal,
        home = homeTotal,
        needsReview = needsReviewTotal,
    )
}

private fun drivenMilesForDate(
    date: LocalDate,
    sessions: List<AwaySession>,
    intervals: List<ActivityInterval>,
    now: Instant,
    zoneId: ZoneId,
): Double = sessions
    .filter { it.countsTowardTotals && it.drivenMiles > 0.0 }
    .sumOf { session ->
        val sessionRange = session.toRange(now) ?: return@sumOf 0.0
        val homeRanges = intervals
            .filter { it.sessionId == session.id && it.bucket == ActivityBucket.HOME }
            .map { InstantRange(it.start, it.end) }
            .merge()
        val totalWorkRanges = listOf(sessionRange).subtract(homeRanges).merge()
        if (sessionRange.duration.isZero || sessionRange.duration.isNegative) {
            0.0
        } else {
            val dateWorkDuration = totalWorkRanges
                .constrainTo(listOf(date.instantRange(zoneId)))
                .totalDuration()
            session.drivenMiles * dateWorkDuration.toMillis().toDouble() / sessionRange.duration.toMillis().toDouble()
        }
    }

private fun normalizedBucketTotals(
    home: Duration,
    drive: Duration,
    site: Duration,
    idle: Duration,
    needsReview: Duration,
): Map<ActivityBucket, Duration> = ActivityBucket.entries.associateWith { bucket ->
    when (bucket) {
        ActivityBucket.HOME -> home
        ActivityBucket.DRIVE -> drive
        ActivityBucket.SITE -> site
        ActivityBucket.IDLE -> idle
        ActivityBucket.NEEDS_REVIEW -> needsReview
    }
}

private fun zeroBucketTotals(): Map<ActivityBucket, Duration> = ActivityBucket.entries.associateWith { Duration.ZERO }
