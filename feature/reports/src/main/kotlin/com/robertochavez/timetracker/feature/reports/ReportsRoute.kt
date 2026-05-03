package com.robertochavez.timetracker.feature.reports

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerCard
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMetricRow
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle

@Composable
fun ReportsRoute(modifier: Modifier = Modifier, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TimeTrackerScreen(modifier = modifier) {
        item {
            TimeTrackerScreenTitle(
                title = "Reports",
                subtitle = "Away time, miles, and activity buckets by period.",
            )
        }
        items(state.reports, key = { it.title }) { report ->
            TimeTrackerCard {
                Text(report.title, style = MaterialTheme.typography.titleMedium)
                TimeTrackerMetricRow("Away", report.away)
                TimeTrackerMetricRow("Miles", report.miles)
                TimeTrackerMetricRow("Drive", report.drive)
                TimeTrackerMetricRow("Idle", report.idle)
                TimeTrackerMetricRow("Unclassified", report.unclassified)
            }
        }
    }
}
