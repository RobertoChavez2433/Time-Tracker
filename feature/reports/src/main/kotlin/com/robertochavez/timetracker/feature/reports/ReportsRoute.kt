package com.robertochavez.timetracker.feature.reports

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMetricRow
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPanel
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSectionTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@Composable
fun ReportsRoute(modifier: Modifier = Modifier, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TimeTrackerScreen(modifier = modifier, testTag = TimeTrackerTestTags.REPORTS_SCREEN) {
        item {
            TimeTrackerScreenTitle(
                title = "Reports",
                subtitle = "Headline totals first, with activity buckets below.",
            )
        }
        state.reports.firstOrNull()?.let { today ->
            item {
                TodayReportPanel(today)
            }
        }
        if (state.reports.size > 1) {
            item {
                TimeTrackerSectionTitle("Periods")
            }
        }
        items(state.reports.drop(1), key = { it.title }) { report ->
            ReportDetailPanel(report)
        }
    }
}

@Composable
private fun TodayReportPanel(report: ReportUiModel) {
    TimeTrackerPanel(modifier = Modifier.testTag(report.testTag())) {
        TimeTrackerMutedText(report.title)
        Text(report.away, style = MaterialTheme.typography.headlineMedium)
        TimeTrackerMetricRow("Miles", report.miles)
        TimeTrackerMetricRow("Drive", report.drive)
        TimeTrackerMetricRow("Idle", report.idle)
        TimeTrackerMetricRow("Unclassified", report.unclassified)
    }
}

@Composable
private fun ReportDetailPanel(report: ReportUiModel) {
    TimeTrackerPanel(modifier = Modifier.testTag(report.testTag())) {
        Text(report.title, style = MaterialTheme.typography.titleMedium)
        TimeTrackerMetricRow("Away", report.away)
        TimeTrackerMetricRow("Miles", report.miles)
        TimeTrackerMetricRow("Drive", report.drive)
        TimeTrackerMetricRow("Idle", report.idle)
        TimeTrackerMetricRow("Unclassified", report.unclassified)
    }
}

private fun ReportUiModel.testTag(): String = when (title) {
    "Today" -> TimeTrackerTestTags.REPORTS_TODAY_CARD
    "This week" -> TimeTrackerTestTags.REPORTS_WEEKLY_CARD
    "Current biweekly period" -> TimeTrackerTestTags.REPORTS_BIWEEKLY_CARD
    "This month" -> TimeTrackerTestTags.REPORTS_MONTHLY_CARD
    "This year" -> TimeTrackerTestTags.REPORTS_YEARLY_CARD
    else -> "reports_${title.lowercase().replace(' ', '_')}_card"
}
