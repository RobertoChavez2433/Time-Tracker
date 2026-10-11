package com.robertochavez.timetracker.feature.reports

import com.robertochavez.timetracker.core.common.domain.SessionSplitter
import com.robertochavez.timetracker.core.common.model.DateRange
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class WorksiteVisitUiModel(
    val id: String,
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
    val name: String,
    val projectNumber: String?,
    val duration: Duration,
    val active: Boolean,
)

data class WorksiteSummaryUiModel(
    val id: String,
    val name: String,
    val projectNumber: String?,
    val total: String,
    val visits: List<WorksiteVisitUiModel>,
)

internal fun List<WorkSiteSession>.worksiteSummaries(range: DateRange, now: Instant, zone: ZoneId): List<WorksiteSummaryUiModel> =
    groupBy(WorkSiteSession::workLocationId).mapNotNull { (id, sessions) ->
        val visits = sessions.flatMap { session ->
            if ((session.end ?: now) <= session.start) return@flatMap emptyList()
            SessionSplitter.splitWorkSiteSessionByDay(session, zone, now)
                .filter { range.contains(it.date) }
                .map { slice ->
                    WorksiteVisitUiModel(
                        session.id,
                        slice.date,
                        slice.start,
                        slice.end,
                        slice.siteLabel,
                        slice.projectNumber,
                        slice.duration,
                        slice.provisional,
                    )
                }
        }.sortedByDescending { it.start }
        val latest = visits.firstOrNull() ?: return@mapNotNull null
        WorksiteSummaryUiModel(
            id = id,
            name = latest.name,
            projectNumber = latest.projectNumber,
            total = visits.fold(Duration.ZERO) { total, visit -> total.plus(visit.duration) }.formatHours(),
            visits = visits,
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
