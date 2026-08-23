package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.DailyReport
import com.robertochavez.timetracker.core.common.model.HubDailySummary
import com.robertochavez.timetracker.core.common.model.ProjectDaySummary
import com.robertochavez.timetracker.core.common.model.SummaryStatus
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun DailyReport.toHubDailySummary(): HubDailySummary = HubDailySummary(
    sourceId = "day:v1:$date",
    date = date,
    totalWorkMinutes = work.toMinutes(),
    driveMinutes = drive.toMinutes(),
    siteMinutes = site.toMinutes(),
    idleMinutes = idle.toMinutes(),
    needsReviewMinutes = needsReview.toMinutes(),
    drivenMiles = drivenMiles,
)

object ProjectDaySummaryCalculator {
    fun calculate(sessions: List<WorkSiteSession>, zoneId: ZoneId, now: Instant): List<ProjectDaySummary> = sessions
        .flatMap { SessionSplitter.splitWorkSiteSessionByDay(it, zoneId, now) }
        .filter { !it.projectNumber.isNullOrBlank() }
        .groupBy { it.workLocationId to it.date }
        .map { (key, slices) ->
            val (workLocationId, date) = key
            val projectNumber = requireNotNull(slices.first().projectNumber)
            ProjectDaySummary(
                sourceId = "project-day:v1:$workLocationId:$date",
                date = date,
                workLocationId = workLocationId,
                projectNumber = projectNumber,
                siteLabel = slices.maxBy { it.end }.siteLabel,
                firstArrival = slices.minOf { it.start },
                lastDeparture = slices.maxOf { it.end },
                onsiteMinutes = slices.sumOf { it.duration.toMinutes() },
                visitCount = slices.size,
                status = when {
                    slices.any { it.provisional } || date == LocalDate.ofInstant(now, zoneId) -> SummaryStatus.PROVISIONAL
                    else -> SummaryStatus.FINAL
                },
            )
        }.sortedWith(compareBy(ProjectDaySummary::date, ProjectDaySummary::projectNumber))
}
