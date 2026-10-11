package com.robertochavez.timetracker.feature.reports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerEditorField
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMetricRow
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPanel
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSecondaryButton
import java.time.YearMonth

@Composable
fun HistoryRoute(onDay: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val start by viewModel.startInput.collectAsStateWithLifecycle()
    val end by viewModel.endInput.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    ReportRefresh(viewModel::refresh)
    TimeTrackerScreen(testTag = "history_screen") {
        item { TimeTrackerScreenTitle("History") }
        item {
            Box {
                TimeTrackerSecondaryButton("Range: ${state.mode.label}", { expanded = true }, Modifier.testTag("history_range"))
                DropdownMenu(expanded, { expanded = false }) {
                    HistoryRangeMode.entries.forEach { mode ->
                        DropdownMenuItem(text = { Text(mode.label) }, onClick = {
                            viewModel.selectRange(mode)
                            expanded = false
                        })
                    }
                }
            }
        }
        if (state.mode == HistoryRangeMode.CUSTOM) {
            item { TimeTrackerEditorField("Start (YYYY-MM-DD)", start, viewModel::updateStart, "history_start") }
            item { TimeTrackerEditorField("End (YYYY-MM-DD)", end, viewModel::updateEnd, "history_end") }
        }
        if (state.error.isNotBlank()) item { Text(state.error, color = MaterialTheme.colorScheme.error) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(viewModel::previousMonth, Modifier.testTag("history_previous_month")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous month")
                }
                Text(
                    state.month.takeIf { it.isNotBlank() }?.let { YearMonth.parse(it).toRangeTitle() }.orEmpty(),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(viewModel::nextMonth, Modifier.testTag("history_next_month")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next month")
                }
            }
            HistoryCalendar(state.calendar, onDay)
            TimeTrackerMutedText("Marked dates have recorded time. Tap a date for totals and sessions.")
        }
        item {
            TimeTrackerPanel {
                TimeTrackerMutedText(state.title)
                TimeTrackerMetricRow("Home", state.summary.home)
                TimeTrackerMetricRow("Work", state.summary.work)
                TimeTrackerMetricRow("Drive", state.summary.drive)
                TimeTrackerMetricRow("Site", state.summary.site)
                TimeTrackerMetricRow("Idle", state.summary.idle)
                IdleExplanation()
                TimeTrackerMetricRow("Miles", state.summary.miles)
            }
        }
    }
}
