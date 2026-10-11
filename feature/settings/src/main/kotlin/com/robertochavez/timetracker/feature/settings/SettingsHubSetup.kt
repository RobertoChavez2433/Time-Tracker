package com.robertochavez.timetracker.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags
import com.robertochavez.timetracker.core.sync.HubProviderGateState
import com.robertochavez.timetracker.core.sync.HubTransportKind

@Composable
internal fun HubSetup(state: SettingsUiState, viewModel: SettingsViewModel, open: (String) -> Unit) {
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(viewModel::setHubOutboxTree) }
    SettingsGroup("1 · Outbox folder") {
        SettingsLink(
            if (state.hubOutboxConfigured) "Change outbox folder" else "Choose outbox folder",
            if (state.hubOutboxConfigured) "Folder selected · changing it requires verification again" else "Select your Work Hub folder",
            TimeTrackerTestTags.SETTINGS_HUB_OUTBOX_BUTTON,
            { picker.launch(null) },
        )
    }
    SettingsGroup("2 · First sync date") {
        SettingsDateRow("Include records starting", state.hubActivationDate, TimeTrackerTestTags.SETTINGS_HUB_ACTIVATION_FIELD) {
            viewModel.setHubActivationDate(it)
        }
    }
    SettingsNote("The first sync date saves when you choose it. Earlier history remains on this phone.")
    SettingsGroup("3 · Verify the connection") {
        SettingsLink("Connection checks", providerGateSummary(state.hubProviderGateState), "settings_hub_checks", { open("hub_checks") })
    }
    if (state.hubFallbackAllowed) {
        SettingsGroup("Alternative connection") {
            SettingsLink("Notion fallback", "Available after the failed OneDrive check", "settings_hub_fallback", { open("hub_fallback") })
        }
    }
    if (state.hubTransport == HubTransportKind.DIRECT_NOTION && state.hubOutboxConfigured) {
        TextButton(onClick = { viewModel.setHubTransport(HubTransportKind.DOCUMENT_TREE) }) { Text("Use OneDrive outbox") }
    }
    if (state.hubActivationDate.isNotBlank()) {
        TextButton(onClick = { viewModel.setHubActivationDate("") }, modifier = Modifier.testTag("settings_hub_clear_date")) {
            Text("Clear first sync date to pause exports")
        }
    }
}

internal fun LazyListScope.hubChecks(state: SettingsUiState, viewModel: SettingsViewModel) {
    item {
        SettingsGroup("Connection check status") {
            Column(Modifier.padding(12.dp).testTag(TimeTrackerTestTags.SETTINGS_HUB_PROVIDER_STATUS)) {
                SettingsNote(providerGateSummary(state.hubProviderGateState))
            }
        }
    }
    item {
        TimeTrackerPrimaryButton(
            if (state.runningAction == "provider") "Checking…" else "Check folder access",
            viewModel::verifyHubProvider,
            Modifier.testTag(TimeTrackerTestTags.SETTINGS_HUB_VERIFY_PROVIDER_BUTTON),
            enabled = state.hubOutboxConfigured && state.runningAction == null,
        )
    }
    if (!state.hubOutboxConfigured) item { SettingsNote("Choose an outbox folder in connection setup first.") }
    if (state.hubProviderGateState == HubProviderGateState.BASIC_PASSED || state.hubProviderGateState == HubProviderGateState.FULL_FAILED) {
        item {
            SettingsNote(
                "On the work phone, verify access after app restart and reboot, a background WorkManager write, receipt reading, " +
                    "and unattended OneDrive sync to the work laptop. Record the result only after completing those checks.",
            )
        }
        item { RecordProviderGateButton(viewModel::recordFullHubProviderGate) }
    }
}

internal fun providerGateSummary(state: HubProviderGateState): String = when (state) {
    HubProviderGateState.NOT_RUN -> "Folder access has not been checked"
    HubProviderGateState.BASIC_PASSED -> "Folder check passed · work-phone verification still required"
    HubProviderGateState.BASIC_FAILED -> "Folder check failed · Notion fallback available"
    HubProviderGateState.FULL_PASSED -> "Work-phone verification passed"
    HubProviderGateState.FULL_FAILED -> "Work-phone verification failed · Notion fallback available"
}

@Composable
private fun RecordProviderGateButton(onResult: (Boolean) -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    TimeTrackerPrimaryButton("Record verification result", { confirming = true })
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Work-phone verification") },
            text = { Text("Have all restart, reboot, background-write, receipt and laptop-sync checks passed on the work phone?") },
            confirmButton = {
                TextButton({
                    confirming = false
                    onResult(true)
                }) { Text("All checks passed") }
            },
            dismissButton = {
                Column {
                    TextButton({
                        confirming = false
                        onResult(false)
                    }) { Text("A check failed") }
                    TextButton({ confirming = false }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
internal fun DiscardFailedRecordsButton(state: SettingsUiState, discard: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    TextButton({ confirming = true }, enabled = state.runningAction == null) { Text("Discard failed sync records") }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Discard failed sync records?") },
            text = { Text("This discards ${state.hubFailedCount} failed queue records. Local tracking history is unchanged.") },
            confirmButton = {
                TextButton({
                    confirming = false
                    discard()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton({ confirming = false }) { Text("Cancel") } },
        )
    }
}
