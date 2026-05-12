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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMetricRow
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPanel
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerQuietButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSecondaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSectionTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSettingSection
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@Composable
fun TrackingRoute(modifier: Modifier = Modifier, viewModel: TrackingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TimeTrackerScreen(modifier = modifier, testTag = TimeTrackerTestTags.TRACKING_SCREEN) {
        item {
            TimeTrackerScreenTitle(
                title = "Tracking",
                subtitle = "Start or stop an away session, then adjust details only when needed.",
            )
        }
        item {
            TimeTrackerSettingSection(title = "Current state") {
                Text(state.activeSummary, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeTrackerPrimaryButton(
                        text = "Start",
                        onClick = viewModel::startManualSession,
                        modifier = Modifier.testTag(TimeTrackerTestTags.TRACKING_START_BUTTON),
                    )
                    TimeTrackerPrimaryButton(
                        text = "Stop",
                        onClick = viewModel::stopActiveSession,
                        modifier = Modifier.testTag(TimeTrackerTestTags.TRACKING_STOP_BUTTON),
                        enabled = state.hasActiveSession,
                    )
                }
            }
        }
        item {
            TimeTrackerSectionTitle("Manual corrections")
        }
        if (state.sessions.isEmpty()) {
            item {
                TimeTrackerPanel {
                    TimeTrackerMutedText("Completed sessions appear here after tracking stops.")
                }
            }
        }
        items(state.sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                actions = SessionActions(
                    onEdit = { viewModel.editSession(session.id) },
                    onCancelEdit = { viewModel.cancelEdit(session.id) },
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
    val onEdit: () -> Unit,
    val onCancelEdit: () -> Unit,
    val onToggleCounts: (Boolean) -> Unit,
    val onStartChange: (String) -> Unit,
    val onEndChange: (String) -> Unit,
    val onMilesChange: (String) -> Unit,
    val onSaveWindow: () -> Unit,
)

@Composable
private fun SessionCard(session: SessionUiModel, actions: SessionActions) {
    val idPrefix = session.id.take(8)
    TimeTrackerPanel(modifier = Modifier.testTag(TimeTrackerTestTags.trackingSessionCard(idPrefix))) {
        Text(session.title, style = MaterialTheme.typography.titleMedium)
        TimeTrackerMutedText(session.subtitle)
        TimeTrackerMetricRow("Duration", session.duration)
        TimeTrackerMetricRow("Miles", session.miles)
        TimeTrackerMetricRow("Status", session.inclusionStatus)
        TimeTrackerMutedText(session.classificationSummary)
        if (session.isEditing) {
            SessionEditControls(session = session, actions = actions, idPrefix = idPrefix)
        } else {
            TimeTrackerSecondaryButton(
                text = "Edit",
                onClick = actions.onEdit,
                modifier = Modifier.testTag(TimeTrackerTestTags.trackingSessionEditButton(idPrefix)),
            )
        }
    }
}

@Composable
private fun SessionEditControls(session: SessionUiModel, actions: SessionActions, idPrefix: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Include in dashboard")
        Switch(
            checked = session.countsTowardTotals,
            onCheckedChange = actions.onToggleCounts,
            modifier = Modifier.testTag(TimeTrackerTestTags.trackingSessionCountsSwitch(idPrefix)),
        )
    }
    OutlinedTextField(
        value = session.editStart,
        onValueChange = actions.onStartChange,
        label = { Text("Start date and time") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TimeTrackerTestTags.trackingSessionStartField(idPrefix)),
    )
    OutlinedTextField(
        value = session.editEnd,
        onValueChange = actions.onEndChange,
        label = { Text("End date and time") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TimeTrackerTestTags.trackingSessionEndField(idPrefix)),
    )
    OutlinedTextField(
        value = session.editDrivenMiles,
        onValueChange = actions.onMilesChange,
        label = { Text("Miles") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TimeTrackerTestTags.trackingSessionMilesField(idPrefix)),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeTrackerQuietButton(
            text = "Cancel",
            onClick = actions.onCancelEdit,
            modifier = Modifier.testTag(TimeTrackerTestTags.trackingSessionCancelEditButton(idPrefix)),
        )
        TimeTrackerSecondaryButton(
            text = "Save",
            onClick = actions.onSaveWindow,
            modifier = Modifier.testTag(TimeTrackerTestTags.trackingSessionSaveButton(idPrefix)),
        )
    }
}
