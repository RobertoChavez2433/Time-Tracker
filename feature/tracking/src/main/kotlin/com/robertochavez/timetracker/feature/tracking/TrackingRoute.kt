package com.robertochavez.timetracker.feature.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerCard
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSectionTitle

@Composable
fun TrackingRoute(modifier: Modifier = Modifier, viewModel: TrackingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TimeTrackerScreen(modifier = modifier) {
        item {
            TimeTrackerScreenTitle(
                title = "Tracking",
                subtitle = "Manual controls and corrections for away sessions.",
            )
        }
        item {
            TimeTrackerCard {
                Text(state.activeSummary, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeTrackerPrimaryButton(text = "Start", onClick = viewModel::startManualSession)
                    TimeTrackerPrimaryButton(text = "Stop", onClick = viewModel::stopActiveSession, enabled = state.hasActiveSession)
                }
            }
        }
        item {
            TimeTrackerSectionTitle("Sessions")
        }
        items(state.sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                actions = SessionActions(
                    onToggleCounts = { viewModel.setCountsTowardTotals(session.id, it) },
                    onStartChange = { viewModel.updateEditStart(session.id, it) },
                    onEndChange = { viewModel.updateEditEnd(session.id, it) },
                    onMilesChange = { viewModel.updateEditDrivenMiles(session.id, it) },
                    onSaveWindow = { viewModel.saveSessionCorrections(session.id) },
                ),
            )
        }
    }
}

private data class SessionActions(
    val onToggleCounts: (Boolean) -> Unit,
    val onStartChange: (String) -> Unit,
    val onEndChange: (String) -> Unit,
    val onMilesChange: (String) -> Unit,
    val onSaveWindow: () -> Unit,
)

@Composable
private fun SessionCard(session: SessionUiModel, actions: SessionActions) {
    TimeTrackerCard {
        Text(session.title, style = MaterialTheme.typography.titleMedium)
        TimeTrackerMutedText(session.subtitle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Counts toward totals")
            Switch(checked = session.countsTowardTotals, onCheckedChange = actions.onToggleCounts)
        }
        OutlinedTextField(
            value = session.editStart,
            onValueChange = actions.onStartChange,
            label = { Text("Start ISO instant") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = session.editEnd,
            onValueChange = actions.onEndChange,
            label = { Text("End ISO instant") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = session.editDrivenMiles,
            onValueChange = actions.onMilesChange,
            label = { Text("Miles driven") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TimeTrackerPrimaryButton(text = "Save Manual Correction", onClick = actions.onSaveWindow)
    }
}
