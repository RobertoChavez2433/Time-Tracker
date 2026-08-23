package com.robertochavez.timetracker.core.common.model

import java.time.Instant
import java.time.LocalDate

enum class SummaryStatus { PROVISIONAL, FINAL, NEEDS_REVIEW }

data class HubDailySummary(
    val sourceId: String,
    val date: LocalDate,
    val totalWorkMinutes: Long,
    val driveMinutes: Long,
    val siteMinutes: Long,
    val idleMinutes: Long,
    val needsReviewMinutes: Long,
    val drivenMiles: Double,
)

data class ProjectDaySummary(
    val sourceId: String,
    val date: LocalDate,
    val workLocationId: String,
    val projectNumber: String,
    val siteLabel: String,
    val firstArrival: Instant,
    val lastDeparture: Instant,
    val onsiteMinutes: Long,
    val visitCount: Int,
    val status: SummaryStatus,
)
