package com.robertochavez.timetracker.feature.tracking

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText

@Composable
fun TrackingStatus(viewModel: TrackingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var menu by rememberSaveable { mutableStateOf(false) }
    val actionLabel = if (state.hasActiveSession) "Stop tracking" else "Start manually"
    val action = if (state.hasActiveSession) viewModel::stopActiveSession else viewModel::startManualSession
    Column {
        Row(Modifier.fillMaxWidth().testTag("tracking_status"), verticalAlignment = Alignment.CenterVertically) {
            Text(state.activeSummary, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Box {
                TextButton({ menu = true }, Modifier.testTag("tracking_menu")) { Text("Controls") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(actionLabel) },
                        onClick = {
                            menu = false
                            action()
                        },
                        enabled = !state.busy,
                    )
                }
            }
        }
        if (state.error.isNotBlank()) TimeTrackerMutedText(state.error)
    }
}
