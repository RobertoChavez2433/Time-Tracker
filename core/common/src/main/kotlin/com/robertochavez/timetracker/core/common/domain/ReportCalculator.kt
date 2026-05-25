package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.DailyReport
import com.robertochavez.timetracker.core.common.model.DateRange
import com.robertochavez.timetracker.core.common.model.DriveMovementObservation
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
import java.time.temporal.ChronoUnit
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
        movementObservations: List<DriveMovementObservation> = emptyList(),
    ): DailyReport {
        if (!workSchedule.isTrackable(date)) {
            return DailyReport(date, Duration.ZERO, zeroBucketTotals(), drivenMiles = 0.0)
        }
        return dailyReport(
            date = date,
            evidence = ReportEvidence(sessions, intervals, workSiteSessions, movementObservations),
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
        movementObservations: List<DriveMovementObservation> = emptyList(),
    ): PeriodReport {
        val weekStart = dateInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return period(DateRange(weekStart, weekStart.plusWeeks(1)), sessions, intervals, now, workSiteSessions, movementObservations)
    }

    fun biweekly(
        dateInPeriod: LocalDate,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
        workSiteSessions: List<WorkSiteSession> = emptyList(),
        movementObservations: List<DriveMovementObservation> = emptyList(),
    ): PeriodReport {
        val anchor = payPeriodSettings.biweeklyAnchorStartDate
        val daysSinceAnchor = Math.floorMod(ChronoUnit.DAYS.between(anchor, dateInPeriod).toInt(), BIWEEKLY_DAYS)
        val start = dateInPeriod.minusDays(daysSinceAnchor.toLong())
        return period(
            DateRange(start, start.plusDays(BIWEEKLY_DAYS.toLong())),
            sessions,
            intervals,
            now,
            workSiteSessions,
            movementObservations,
        )
    }

    fun monthly(
        month: YearMonth,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
        workSiteSessions: List<WorkSiteSession> = emptyList(),
        movementObservations: List<DriveMovementObservation> = emptyList(),
    ): PeriodReport {
        val start = month.atDay(1)
        return period(DateRange(start, month.plusMonths(1).atDay(1)), sessions, intervals, now, workSiteSessions, movementObservations)
    }

    fun yearly(
        year: Int,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
        workSiteSessions: List<WorkSiteSession> = emptyList(),
        movementObservations: List<DriveMovementObservation> = emptyList(),
    ): PeriodReport {
        val start = LocalDate.of(year, 1, 1)
        return period(DateRange(start, start.plusYears(1)), sessions, intervals, now, workSiteSessions, movementObservations)
    }

    private fun period(
        range: DateRange,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
        workSiteSessions: List<WorkSiteSession>,
        movementObservations: List<DriveMovementObservation>,
    ): PeriodReport {
        val dailyReports = generateSequence(range.startInclusive) { it.plusDays(1) }
            .takeWhile { it.isBefore(range.endExclusive) }
            .map { daily(it, sessions, intervals, now, workSiteSessions, movementObservations) }
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

private const val BIWEEKLY_DAYS = 14

private data class ReportEvidence(
    val sessions: List<AwaySession>,
    val intervals: List<ActivityInterval>,
    val workSiteSessions: List<WorkSiteSession>,
    val movementObservations: List<DriveMovementObservation>,
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
    val movementDriveRanges = evidence.movementObservations
        .filter { it.sessionId in countedSessionIds && it.vehicleLike && !it.atWork }
        .map { InstantRange(it.start, it.end) }
        .constrainTo(listOf(dateRange))
        .merge()
        .filter { it.duration >= MovementDriveInference.SUSTAINED_DRIVE_DURATION }
    val driveRanges = (intervalDriveRanges + movementDriveRanges)
        .merge()
        .constrainTo(workRanges)
        .subtract(siteWorkRanges)
        .merge()
    val driveTotal = driveRanges.totalDuration()
    val awayTotal = workRanges.totalDuration()
    val idleTotal = awayTotal.minus(driveTotal).minus(siteTotal).coerceNonNegative()
    val homeTotal = awayRanges.overlapDuration(homeRanges)
    val bucketTotals = normalizedBucketTotals(
        home = homeTotal,
        drive = driveTotal,
        site = siteTotal,
        idle = idleTotal,
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
        unclassified = Duration.ZERO,
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

private fun normalizedBucketTotals(home: Duration, drive: Duration, site: Duration, idle: Duration): Map<ActivityBucket, Duration> =
    ActivityBucket.entries.associateWith { bucket ->
        when (bucket) {
            ActivityBucket.HOME -> home
            ActivityBucket.DRIVE -> drive
            ActivityBucket.SITE -> site
            ActivityBucket.IDLE -> idle
            ActivityBucket.UNCLASSIFIED -> Duration.ZERO
        }
    }

private fun zeroBucketTotals(): Map<ActivityBucket, Duration> = ActivityBucket.entries.associateWith { Duration.ZERO }

private fun LocalDate.instantRange(zoneId: ZoneId): InstantRange = InstantRange(
    start = atStartOfDay(zoneId).toInstant(),
    end = plusDays(1).atStartOfDay(zoneId).toInstant(),
)

private fun WorkSiteSession.toRange(now: Instant): InstantRange? {
    val effectiveEnd = end ?: now
    return if (effectiveEnd > start) InstantRange(start, effectiveEnd) else null
}

private fun AwaySession.toRange(now: Instant): InstantRange? {
    val effectiveEnd = end ?: now
    return if (effectiveEnd > start) InstantRange(start, effectiveEnd) else null
}

private data class InstantRange(val start: Instant, val end: Instant) {
    init {
        require(end > start) { "Instant range end must be after start." }
    }

    val duration: Duration = Duration.between(start, end)

    fun intersect(other: InstantRange): InstantRange? {
        val overlapStart = maxOf(start, other.start)
        val overlapEnd = minOf(end, other.end)
        return if (overlapEnd > overlapStart) InstantRange(overlapStart, overlapEnd) else null
    }
}

private fun List<InstantRange>.merge(): List<InstantRange> {
    if (isEmpty()) return emptyList()
    val sorted = sortedBy { it.start }
    val merged = mutableListOf<InstantRange>()
    var current = sorted.first()
    sorted.drop(1).forEach { next ->
        current = if (!next.start.isAfter(current.end)) {
            InstantRange(current.start, maxOf(current.end, next.end))
        } else {
            merged += current
            next
        }
    }
    merged += current
    return merged
}

private fun List<InstantRange>.totalDuration(): Duration = fold(Duration.ZERO) { total, range -> total.plus(range.duration) }

private fun List<InstantRange>.overlapDuration(other: List<InstantRange>): Duration =
    flatMap { range -> other.mapNotNull(range::intersect) }
        .merge()
        .totalDuration()

private fun List<InstantRange>.constrainTo(bounds: List<InstantRange>): List<InstantRange> =
    flatMap { range -> bounds.mapNotNull(range::intersect) }
        .merge()

private fun List<InstantRange>.subtract(excluded: List<InstantRange>): List<InstantRange> {
    if (isEmpty() || excluded.isEmpty()) return this
    return fold(emptyList<InstantRange>()) { ranges, range ->
        ranges + excluded.fold(listOf(range)) { remaining, excludedRange ->
            remaining.flatMap { it.subtract(excludedRange) }
        }
    }.merge()
}

private fun InstantRange.subtract(excluded: InstantRange): List<InstantRange> {
    val overlap = intersect(excluded) ?: return listOf(this)
    val before = if (overlap.start > start) InstantRange(start, overlap.start) else null
    val after = if (overlap.end < end) InstantRange(overlap.end, end) else null
    return listOfNotNull(before, after)
}
