package com.robertochavez.timetracker.feature.reports

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerCompactRow
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMetricRow
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPanel
import com.robertochavez.timetracker.core.designsystem.TimeTrackerQuietButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSectionTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DayDetailRoute(onBack: () -> Unit, onCorrection: (String) -> Unit, viewModel: DayDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportRefresh(viewModel::refresh)
    TimeTrackerScreen(testTag = "day_detail_screen") {
        item {
            TimeTrackerQuietButton("Back", onBack)
            TimeTrackerScreenTitle(state.date)
        }
        item {
            state.totals?.let { totals ->
                TimeTrackerPanel {
                    TimeTrackerMetricRow("Home", totals.home)
                    TimeTrackerMetricRow("Work", totals.work)
                    TimeTrackerMetricRow("Drive", totals.drive)
                    TimeTrackerMetricRow("Site", totals.site)
                    TimeTrackerMetricRow("Idle", totals.idle)
                    IdleExplanation()
                    TimeTrackerMetricRow("Miles", totals.miles)
                }
            }
        }
        item { TimeTrackerSectionTitle("Worksite visits") }
        if (state.worksites.isEmpty()) item { TimeTrackerMutedText("No worksite visits recorded.") }
        items(state.worksites, key = { it.id }) { site -> WorksiteVisits(site) }
        item {
            TimeTrackerSectionTitle("Recorded sessions")
            TimeTrackerMutedText("Corrections apply to the entire session, including any other dates it spans.")
        }
        if (state.sessions.isEmpty()) item { TimeTrackerMutedText("No sessions recorded for this date.") }
        items(state.sessions, key = { it.id }) { session ->
            TimeTrackerCompactRow(
                "${session.start.displayTime()} – ${session.end?.displayTime() ?: "Active"}",
                "${session.drivenMiles} miles · ${if (session.countsTowardTotals) "Included" else "Excluded"}",
                "Edit",
                { onCorrection(session.id) },
                Modifier.testTag("day_session_${session.id}"),
            )
        }
    }
}

@Composable
fun WorksiteDetailRoute(onBack: () -> Unit, onDay: (String) -> Unit, viewModel: WorksiteDetailViewModel = hiltViewModel()) {
    val site by viewModel.uiState.collectAsStateWithLifecycle()
    ReportRefresh(viewModel::refresh)
    TimeTrackerScreen(testTag = "worksite_detail_screen") {
        item { TimeTrackerQuietButton("Back", onBack) }
        item { TimeTrackerScreenTitle(site?.name ?: "Worksite visits", "Visits for the selected week") }
        site?.let { site ->
            item { TimeTrackerMutedText("${site.projectNumber?.let { "Job $it" } ?: "Unassigned"} · ${site.total} onsite") }
            items(site.visits, key = { "${it.id}-${it.date}" }) { visit ->
                TimeTrackerCompactRow(
                    visit.date.toString(),
                    visit.description(),
                    "Day",
                    { onDay(visit.date.toString()) },
                )
            }
        }
    }
}

@Composable
private fun WorksiteVisits(site: WorksiteSummaryUiModel) {
    TimeTrackerPanel {
        Text("${site.name} · ${site.total}")
        site.visits.forEach { TimeTrackerMutedText(it.description()) }
    }
}

private fun WorksiteVisitUiModel.description(): String = "$name · ${projectNumber?.let { "Job $it" } ?: "Unassigned"}\n" +
    "${start.displayTime()} – ${end.displayTime()} · ${duration.formatHours()}${if (active) " · Active" else ""}"

private fun Instant.displayTime(): String = atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
