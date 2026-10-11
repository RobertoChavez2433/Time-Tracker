package com.robertochavez.timetracker.feature.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerDiscardDialog
import com.robertochavez.timetracker.core.designsystem.TimeTrackerEditorActions
import com.robertochavez.timetracker.core.designsystem.TimeTrackerEditorField
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSettingRow

@Composable
fun SessionCorrectionRoute(onBack: () -> Unit, viewModel: SessionCorrectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var discard by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    BackHandler {
        when {
            state.saving -> Unit
            state.dirty -> discard = true
            else -> onBack()
        }
    }
    TimeTrackerScreen(Modifier.imePadding(), "session_correction_screen") {
        item { TimeTrackerScreenTitle("Edit Session") }
        if (state.loading) {
            item { Text("Loading…") }
        } else {
            item { TimeTrackerMutedText("Times include a timezone to keep midnight and daylight-saving corrections precise. Z means UTC.") }
            item {
                TimeTrackerEditorField("Start date and time", state.start, {
                    viewModel.edit(start = it)
                }, "correction_start", !state.saving)
            }
            item {
                TimeTrackerEditorField("End date and time (blank if active)", state.end, {
                    viewModel.edit(end = it)
                }, "correction_end", !state.saving)
            }
            item { TimeTrackerEditorField("Miles", state.miles, { viewModel.edit(miles = it) }, "correction_miles", !state.saving) }
            item {
                TimeTrackerSettingRow("Include in totals") {
                    Switch(state.included, { viewModel.edit(included = it) }, enabled = !state.saving)
                }
            }
            if (state.error.isNotBlank()) item { Text(state.error, color = MaterialTheme.colorScheme.error) }
            item { TimeTrackerEditorActions(viewModel::save, onBack, state.saving) }
        }
    }
    if (discard) TimeTrackerDiscardDialog({ discard = false }, onBack)
}
