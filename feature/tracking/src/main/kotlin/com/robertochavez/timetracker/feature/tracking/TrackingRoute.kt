package com.robertochavez.timetracker.feature.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TrackingRoute(
    modifier: Modifier = Modifier,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Tracking", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(state.activeSummary, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::startManualSession) {
                                Text("Start")
                            }
                            Button(onClick = viewModel::stopActiveSession, enabled = state.hasActiveSession) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }
            item {
                Text("Sessions", style = MaterialTheme.typography.titleLarge)
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
}

private data class SessionActions(
    val onToggleCounts: (Boolean) -> Unit,
    val onStartChange: (String) -> Unit,
    val onEndChange: (String) -> Unit,
    val onMilesChange: (String) -> Unit,
    val onSaveWindow: () -> Unit,
)

@Composable
private fun SessionCard(
    session: SessionUiModel,
    actions: SessionActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(session.title, style = MaterialTheme.typography.titleMedium)
            Text(session.subtitle)
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
            Button(onClick = actions.onSaveWindow) {
                Text("Save Manual Correction")
            }
        }
    }
}
