package com.robertochavez.timetracker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerQuietButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSecondaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@Composable
internal fun OverwriteHomeDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Overwrite home location?") },
        text = { Text("A home location is already saved. Overwrite it with this location?") },
        dismissButton = {
            TimeTrackerQuietButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.testTag(TimeTrackerTestTags.HOME_OVERWRITE_CANCEL_BUTTON),
            )
        },
        confirmButton = {
            TimeTrackerPrimaryButton(
                text = "Overwrite Home",
                onClick = onConfirm,
                modifier = Modifier.testTag(TimeTrackerTestTags.HOME_OVERWRITE_CONFIRM_BUTTON),
            )
        },
    )
}

@Composable
internal fun WorkLocationSaveDialog(latestLabel: String, onCancel: () -> Unit, onAdd: () -> Unit, onReplace: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Save work location?") },
        text = { Text("Add this as another work site, or replace $latestLabel.") },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeTrackerQuietButton(
                    text = "Cancel",
                    onClick = onCancel,
                    modifier = Modifier.testTag(TimeTrackerTestTags.WORK_SAVE_CANCEL_BUTTON),
                )
                TimeTrackerSecondaryButton(
                    text = "Replace Latest",
                    onClick = onReplace,
                    modifier = Modifier.testTag(TimeTrackerTestTags.WORK_REPLACE_LOCATION_BUTTON),
                )
            }
        },
        confirmButton = {
            TimeTrackerPrimaryButton(
                text = "Add Work Site",
                onClick = onAdd,
                modifier = Modifier.testTag(TimeTrackerTestTags.WORK_ADD_LOCATION_BUTTON),
            )
        },
    )
}

internal enum class HomeSaveAction {
    UseCurrent,
    SavePin,
}

internal enum class WorkSaveAction {
    UseCurrent,
    SavePin,
}
