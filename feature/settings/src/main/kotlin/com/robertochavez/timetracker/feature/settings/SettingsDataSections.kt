package com.robertochavez.timetracker.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.robertochavez.timetracker.core.designsystem.TimeTrackerDestructiveButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

internal fun LazyListScope.backupIndex(open: (String) -> Unit) {
    item {
        SettingsGroup("Your tracking history") {
            SettingsLink("Create backup", "Save history, places and settings as JSON", "settings_backup_export", { open("backup_export") })
            SettingsDivider()
            SettingsLink("Restore a backup", "Requires an empty app", "settings_backup_import", { open("backup_import") })
        }
    }
    item { SettingsNote("Backups exclude connection tokens, credentials and folder permissions.") }
}

@Composable
internal fun BackupTransfer(exporting: Boolean, state: SettingsUiState, viewModel: SettingsViewModel) {
    if (exporting) ExportBackup(state, viewModel) else RestoreBackup(state, viewModel)
}

@Composable
private fun ExportBackup(state: SettingsUiState, viewModel: SettingsViewModel) {
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    SettingsNote("Save your history, places and settings as a JSON file. No password required. Keep the file private.")
    TimeTrackerPrimaryButton(
        text = if (state.runningAction == "backup") "Working…" else "Choose where to save",
        onClick = { export.launch("time-tracker-backup.json") },
        enabled = state.runningAction == null,
        modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_BACKUP_EXPORT_BUTTON),
    )
}

@Composable
private fun RestoreBackup(state: SettingsUiState, viewModel: SettingsViewModel) {
    var legacyEncrypted by rememberSaveable { mutableStateOf(false) }
    val passphrase = if (legacyEncrypted) viewModel.legacyBackupPassphrase else ""
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackup(it, passphrase) }
    }
    SettingsNote(
        "Choose a Time Tracker JSON backup. Existing records are never merged or overwritten; this app must have no tracking data.",
    )
    TimeTrackerPrimaryButton(
        text = if (state.runningAction == "backup") "Working…" else "Choose backup",
        onClick = { restore.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
        enabled = state.runningAction == null && (!legacyEncrypted || passphrase.isNotBlank()),
        modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_BACKUP_IMPORT_BUTTON),
    )
    TextButton(onClick = { legacyEncrypted = !legacyEncrypted }) {
        Text(if (legacyEncrypted) "Use a JSON backup" else "Restore an older encrypted backup")
    }
    if (legacyEncrypted) LegacyBackupPassphrase(viewModel)
}

@Composable
private fun LegacyBackupPassphrase(viewModel: SettingsViewModel) {
    // The legacy secret stays only in ViewModel memory, never in Android saved-instance state.
    OutlinedTextField(
        value = viewModel.legacyBackupPassphrase,
        onValueChange = { viewModel.legacyBackupPassphrase = it },
        label = { Text("Original backup passphrase") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(TimeTrackerTestTags.SETTINGS_BACKUP_PASSPHRASE_FIELD),
    )
}

@Composable
internal fun NotificationsCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsGroup("While tracking") {
        SettingsToggle(
            "Compact tracking status",
            "Keep the active notification minimal",
            state.minimalActiveNotificationEnabled,
            TimeTrackerTestTags.SETTINGS_MINIMAL_NOTIFICATION_SWITCH,
            viewModel::setMinimalActiveNotificationEnabled,
        )
        SettingsDivider()
        SettingsToggle(
            "Live timer",
            "Show elapsed time in notifications",
            state.liveTimerNotificationEnabled,
            TimeTrackerTestTags.SETTINGS_LIVE_TIMER_NOTIFICATION_SWITCH,
            viewModel::setLiveTimerNotificationEnabled,
        )
    }
}

@Composable
internal fun PrivacySection(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsGroup("Local automation") {
        SettingsToggle(
            "Allow local automation",
            "Use location and activity for your timesheet",
            state.privacyDisclosureAccepted,
            TimeTrackerTestTags.SETTINGS_PRIVACY_DISCLOSURE_SWITCH,
            viewModel::setPrivacyDisclosureAccepted,
        )
    }
}

@Composable
internal fun LocalDataCard(onDelete: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SettingsGroup("Reset this phone") {
        SettingsLink(
            "Delete local data",
            "Remove saved places, history and preferences",
            TimeTrackerTestTags.SETTINGS_DELETE_LOCAL_DATA_BUTTON,
            { confirming = true },
            warning = true,
        )
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete local data?", Modifier.testTag(TimeTrackerTestTags.SETTINGS_DELETE_CONFIRM_DIALOG)) },
            text = {
                Text("This clears saved locations, sessions, mileage, workdays, pay-period settings, and preferences on this device.")
            },
            dismissButton = {
                TextButton({
                    confirming = false
                }, Modifier.testTag(TimeTrackerTestTags.SETTINGS_DELETE_CONFIRM_CANCEL_BUTTON)) { Text("Cancel") }
            },
            confirmButton = {
                TimeTrackerDestructiveButton(
                    "Delete",
                    {
                        confirming = false
                        onDelete()
                    },
                    Modifier.testTag(TimeTrackerTestTags.SETTINGS_DELETE_CONFIRM_BUTTON),
                )
            },
        )
    }
}
