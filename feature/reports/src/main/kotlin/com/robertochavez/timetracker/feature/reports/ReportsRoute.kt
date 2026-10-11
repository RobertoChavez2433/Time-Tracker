package com.robertochavez.timetracker.feature.reports

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerColors
import com.robertochavez.timetracker.core.designsystem.TimeTrackerCompactRow
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSectionTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags
import kotlinx.coroutines.delay

@Composable
fun ReportsRoute(
    onDay: (String) -> Unit,
    onWorksite: (String, String) -> Unit,
    trackingStatus: @Composable () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportRefresh(viewModel::refreshForResume)
    TimeTrackerScreen(testTag = TimeTrackerTestTags.DASHBOARD_SCREEN) {
        item { TimeTrackerScreenTitle("Overview") }
        item { trackingStatus() }
        item {
            WeekNavigation(state.weeklyLedger.title, state.canNavigateToNextWeek, viewModel::previousWeek, viewModel::nextWeek)
            TimeTrackerMutedText(state.weeklyLedger.dateRange)
        }
        item { WeeklyLedger(state.weeklyLedger, onDay) }
        item { TimeTrackerSectionTitle("Worksites This Week") }
        if (state.weeklyWorksites.isEmpty()) item { TimeTrackerMutedText("No worksite visits in this week.") }
        items(state.weeklyWorksites, key = { it.id }) { site ->
            TimeTrackerCompactRow(
                site.name,
                "${site.projectNumber?.let { "Job $it" } ?: "Unassigned"} · ${site.total} onsite",
                "Visits",
                { onWorksite(site.id, state.selectedWeekStart) },
                Modifier.testTag("weekly_worksite_${site.id}"),
            )
            HorizontalDivider(color = TimeTrackerColors.Divider)
        }
    }
}

@Composable
internal fun ReportRefresh(refresh: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            refresh()
        }
    }
}

@Composable
internal fun WeekNavigation(title: String, nextEnabled: Boolean, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(previous, Modifier.testTag(TimeTrackerTestTags.DASHBOARD_PREVIOUS_WEEK_BUTTON)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous week")
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        IconButton(next, Modifier.testTag(TimeTrackerTestTags.DASHBOARD_NEXT_WEEK_BUTTON), enabled = nextEnabled) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next week")
        }
    }
}
