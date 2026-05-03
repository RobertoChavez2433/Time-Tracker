package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.DailyReport
import com.robertochavez.timetracker.core.common.model.DateRange
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.PeriodReport
import com.robertochavez.timetracker.core.common.model.WorkSchedule
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
    ): DailyReport {
        if (!workSchedule.isTrackable(date)) {
            return DailyReport(date, Duration.ZERO, zeroBucketTotals(), drivenMiles = 0.0)
        }

        val range = DateRange(date, date.plusDays(1))
        val awayTotal = sessions
            .filter { it.countsTowardTotals }
            .flatMap { SessionSplitter.splitByDay(it, zoneId, now) }
            .filter { it.date == date }
            .fold(Duration.ZERO) { total, slice -> total.plus(slice.duration) }

        val bucketTotals = intervals
            .flatMap { SessionSplitter.splitActivityIntervalByDay(it, zoneId) }
            .filter { range.contains(it.date) }
            .groupBy({ it.bucket }, { it.duration })
            .mapValues { (_, durations) -> durations.fold(Duration.ZERO, Duration::plus) }
            .withZeroBuckets()

        return DailyReport(
            date = date,
            totalAway = awayTotal,
            bucketTotals = bucketTotals,
            drivenMiles = drivenMilesForDate(date, sessions, now),
        )
    }

    fun weekly(
        dateInWeek: LocalDate,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
    ): PeriodReport {
        val weekStart = dateInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return period(DateRange(weekStart, weekStart.plusWeeks(1)), sessions, intervals, now)
    }

    fun biweekly(
        dateInPeriod: LocalDate,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
    ): PeriodReport {
        val anchor = payPeriodSettings.biweeklyAnchorStartDate
        val daysSinceAnchor = Math.floorMod(ChronoUnit.DAYS.between(anchor, dateInPeriod).toInt(), BIWEEKLY_DAYS)
        val start = dateInPeriod.minusDays(daysSinceAnchor.toLong())
        return period(DateRange(start, start.plusDays(BIWEEKLY_DAYS.toLong())), sessions, intervals, now)
    }

    fun monthly(
        month: YearMonth,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
    ): PeriodReport {
        val start = month.atDay(1)
        return period(DateRange(start, month.plusMonths(1).atDay(1)), sessions, intervals, now)
    }

    fun yearly(
        year: Int,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
    ): PeriodReport {
        val start = LocalDate.of(year, 1, 1)
        return period(DateRange(start, start.plusYears(1)), sessions, intervals, now)
    }

    private fun period(
        range: DateRange,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        now: Instant,
    ): PeriodReport {
        val dailyReports = generateSequence(range.startInclusive) { it.plusDays(1) }
            .takeWhile { it.isBefore(range.endExclusive) }
            .map { daily(it, sessions, intervals, now) }
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

    private fun drivenMilesForDate(
        date: LocalDate,
        sessions: List<AwaySession>,
        now: Instant,
    ): Double = sessions
        .filter { it.countsTowardTotals && it.drivenMiles > 0.0 }
        .sumOf { session ->
            val sessionEnd = session.end ?: now
            val totalDuration = Duration.between(session.start, sessionEnd)
            if (totalDuration.isZero || totalDuration.isNegative) {
                0.0
            } else {
                val dateDuration = SessionSplitter.splitByDay(session, zoneId, now)
                    .filter { it.date == date }
                    .fold(Duration.ZERO) { total, slice -> total.plus(slice.duration) }
                session.drivenMiles * dateDuration.toMillis().toDouble() / totalDuration.toMillis().toDouble()
            }
        }

    private fun Map<ActivityBucket, Duration>.withZeroBuckets(): Map<ActivityBucket, Duration> = ActivityBucket.entries.associateWith { bucket -> this[bucket] ?: Duration.ZERO }

    private fun zeroBucketTotals(): Map<ActivityBucket, Duration> = ActivityBucket.entries.associateWith { Duration.ZERO }

    private companion object {
        const val BIWEEKLY_DAYS = 14
    }
}
