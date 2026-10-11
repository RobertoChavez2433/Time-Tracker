package com.robertochavez.timetracker.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun TimeTrackerCompactRow(title: String, detail: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = TimeTrackerSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.Small),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.XSmall)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TimeTrackerColors.TextPrimary)
            TimeTrackerMutedText(detail)
        }
        Text(action, style = MaterialTheme.typography.labelLarge, color = TimeTrackerColors.PrimaryOliveDark)
    }
}

@Composable
fun TimeTrackerEditorField(label: String, value: String, onChange: (String) -> Unit, tag: String, enabled: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
fun TimeTrackerEditorActions(onSave: () -> Unit, onCancel: () -> Unit, saving: Boolean, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.Small)) {
        TimeTrackerQuietButton("Cancel", onCancel, Modifier.weight(1f).testTag("editor_cancel"), enabled = !saving)
        TimeTrackerPrimaryButton(
            if (saving) "Saving…" else "Save",
            onSave,
            Modifier.weight(1f).testTag("editor_save"),
            enabled = enabled && !saving,
        )
    }
}

@Composable
fun TimeTrackerDiscardDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Discard unsaved changes?") },
        text = { Text("Your saved data will stay unchanged.") },
        confirmButton = { TimeTrackerQuietButton("Discard", onDiscard, Modifier.testTag("discard_changes")) },
        dismissButton = { TimeTrackerQuietButton("Keep editing", onKeepEditing) },
    )
}
