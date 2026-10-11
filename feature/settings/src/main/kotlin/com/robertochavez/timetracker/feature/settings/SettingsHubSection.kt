package com.robertochavez.timetracker.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

internal fun LazyListScope.hubOverview(state: SettingsUiState, viewModel: SettingsViewModel, open: (String) -> Unit) {
    val detail = if (state.hubReady) state.hubSyncStatus else "Tracking continues on this phone"
    val syncLabel = if (state.runningAction == "sync") "Syncing…" else "Sync now"
    item {
        SettingsGroup("Connection") {
            Column(Modifier.padding(12.dp)) {
                SettingsRowText(state.hubConnectionLabel, detail)
            }
        }
    }
    item {
        if (state.hubReady) {
            TimeTrackerPrimaryButton(
                syncLabel,
                viewModel::syncHubNow,
                Modifier.testTag(TimeTrackerTestTags.SETTINGS_HUB_SYNC_NOW_BUTTON),
                enabled = state.runningAction == null,
            )
        } else {
            TimeTrackerPrimaryButton("Set up Work Hub", { open("hub_setup") }, Modifier.testTag("settings_hub_setup"))
        }
    }
    item { SettingsNote("Only date and project summaries are exported. Home details, locations and routes stay on this phone.") }
    item {
        SettingsGroup("Connection settings") {
            if (state.hubOutboxConfigured || state.hubReady) {
                SettingsLink("Edit connection", "Folder and first sync date", "settings_hub_edit", { open("hub_setup") })
                SettingsDivider()
            }
            SettingsLink(
                "Advanced & troubleshooting",
                "Connection checks and recovery",
                "settings_hub_advanced",
                { open("hub_advanced") },
            )
        }
    }
}

internal fun LazyListScope.hubAdvanced(state: SettingsUiState, viewModel: SettingsViewModel, open: (String) -> Unit) {
    item {
        SettingsGroup("Troubleshooting") {
            SettingsLink("Connection checks", "Folder access and work-phone verification", "settings_hub_checks", { open("hub_checks") })
            if (state.hubFallbackAllowed) {
                SettingsDivider()
                SettingsLink("Notion fallback", "Available after the failed OneDrive check", "settings_hub_fallback", {
                    open("hub_fallback")
                })
            }
        }
    }
    item { SettingsNote(state.hubSyncStatus) }
    if (state.hubReady) {
        item {
            TextButton(
                onClick = viewModel::fullResyncHub,
                enabled = state.runningAction == null,
                modifier = Modifier.testTag("settings_hub_resync"),
            ) {
                Text("Resync from first sync date")
            }
        }
    }
    if (state.hubFailedCount > 0) {
        item { DiscardFailedRecordsButton(state, viewModel::discardFailedHubRecords) }
    }
}
