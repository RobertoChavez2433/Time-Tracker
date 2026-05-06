package com.robertochavez.timetracker.core.common.model

import java.time.Duration
import java.time.LocalDate

data class DailyReport(
    val date: LocalDate,
    val totalAway: Duration,
    val bucketTotals: Map<ActivityBucket, Duration>,
    val drivenMiles: Double,
    val drive: Duration = bucketTotals[ActivityBucket.DRIVE] ?: Duration.ZERO,
    val site: Duration = Duration.ZERO,
) {
    val idle: Duration = totalAway.minus(drive).minus(site).coerceNonNegative()
    val activityIdle: Duration = bucketTotals[ActivityBucket.IDLE] ?: Duration.ZERO
    val unclassified: Duration = bucketTotals[ActivityBucket.UNCLASSIFIED] ?: Duration.ZERO
}

data class PeriodReport(
    val range: DateRange,
    val totalAway: Duration,
    val bucketTotals: Map<ActivityBucket, Duration>,
    val drivenMiles: Double,
    val dailyReports: List<DailyReport>,
) {
    val drive: Duration = dailyReports.fold(Duration.ZERO) { total, report -> total.plus(report.drive) }
    val site: Duration = dailyReports.fold(Duration.ZERO) { total, report -> total.plus(report.site) }
    val idle: Duration = totalAway.minus(drive).minus(site).coerceNonNegative()
}

private fun Duration.coerceNonNegative(): Duration = if (isNegative) Duration.ZERO else this
