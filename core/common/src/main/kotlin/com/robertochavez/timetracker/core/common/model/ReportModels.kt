package com.robertochavez.timetracker.core.common.model

import java.time.Duration
import java.time.LocalDate

data class DailyReport(
    val date: LocalDate,
    val totalAway: Duration,
    val bucketTotals: Map<ActivityBucket, Duration>,
    val drivenMiles: Double,
    val drive: Duration = bucketTotals[ActivityBucket.DRIVE] ?: Duration.ZERO,
    val site: Duration = bucketTotals[ActivityBucket.SITE] ?: Duration.ZERO,
    val idle: Duration = bucketTotals[ActivityBucket.IDLE] ?: Duration.ZERO,
    val home: Duration = bucketTotals[ActivityBucket.HOME] ?: Duration.ZERO,
    val needsReview: Duration = bucketTotals[ActivityBucket.NEEDS_REVIEW] ?: Duration.ZERO,
) {
    val activityIdle: Duration = bucketTotals[ActivityBucket.IDLE] ?: Duration.ZERO
    val work: Duration = totalAway
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
    val idle: Duration = dailyReports.fold(Duration.ZERO) { total, report -> total.plus(report.idle) }
    val home: Duration = dailyReports.fold(Duration.ZERO) { total, report -> total.plus(report.home) }
    val needsReview: Duration = dailyReports.fold(Duration.ZERO) { total, report -> total.plus(report.needsReview) }
    val work: Duration = totalAway
}
