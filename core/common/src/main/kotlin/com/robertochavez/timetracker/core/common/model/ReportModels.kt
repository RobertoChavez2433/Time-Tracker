package com.robertochavez.timetracker.core.common.model

import java.time.Duration
import java.time.LocalDate

data class DailyReport(
    val date: LocalDate,
    val totalAway: Duration,
    val bucketTotals: Map<ActivityBucket, Duration>,
    val drivenMiles: Double,
) {
    val drive: Duration = bucketTotals[ActivityBucket.DRIVE] ?: Duration.ZERO
    val idle: Duration = bucketTotals[ActivityBucket.IDLE] ?: Duration.ZERO
    val unclassified: Duration = bucketTotals[ActivityBucket.UNCLASSIFIED] ?: Duration.ZERO
}

data class PeriodReport(
    val range: DateRange,
    val totalAway: Duration,
    val bucketTotals: Map<ActivityBucket, Duration>,
    val drivenMiles: Double,
    val dailyReports: List<DailyReport>,
)
