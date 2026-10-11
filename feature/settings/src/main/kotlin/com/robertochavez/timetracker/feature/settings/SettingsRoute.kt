package com.robertochavez.timetracker.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerDiscardDialog

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    section: String? = null,
    onSection: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissions = rememberSettingsPermissions()
    val snackbar = remember { SnackbarHostState() }
    var discard by rememberSaveable { mutableStateOf(false) }
    val back = {
        if (section == "schedule" && state.payPeriodEdited) discard = true else onBack()
    }
    BackHandler(enabled = section == "schedule" && state.payPeriodEdited) { discard = true }
    LaunchedEffect(state.statusMessage) {
        if (state.statusMessage.isNotBlank()) {
            snackbar.showSnackbar(state.statusMessage)
            viewModel.clearStatus()
        }
    }
    SettingsScreen(settingsTitle(section), if (section != null) back else null, snackbar, modifier) {
        when (section) {
            null -> settingsIndex(state, permissions.state, onSection)
            "tracking" -> trackingSettings(state, permissions, viewModel, onSection)
            "schedule" -> {
                block { WorkdaysSection(state.workdays, viewModel::setDayTrackable) }
                block { PayPeriodCard(state, viewModel) }
            }
            "notifications" -> notificationSettings(state, permissions, viewModel)
            "hub" -> hubOverview(state, viewModel, onSection)
            "hub_setup" -> block { HubSetup(state, viewModel, onSection) }
            "hub_checks" -> hubChecks(state, viewModel)
            "hub_advanced" -> hubAdvanced(state, viewModel, onSection)
            "hub_fallback" -> block { HubFallback(state, viewModel) }
            "backup" -> backupIndex(onSection)
            "backup_export", "backup_import" -> block { BackupTransfer(section == "backup_export", state, viewModel) }
            "privacy" -> {
                block { PrivacySection(state, viewModel) }
                block {
                    SettingsNote(
                        "Location and activity support local tracking. Work Hub receives only date and project summaries when configured.",
                    )
                }
                block { LocalDataCard(viewModel::deleteAllLocalData) }
            }
        }
    }
    if (discard) {
        TimeTrackerDiscardDialog(
            onKeepEditing = { discard = false },
            onDiscard = {
                discard = false
                viewModel.discardPayPeriodEdits()
                onBack()
            },
        )
    }
}

private fun LazyListScope.block(content: @Composable ColumnScope.() -> Unit) {
    item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

private fun LazyListScope.trackingSettings(
    state: SettingsUiState,
    permissions: SettingsPermissionActions,
    viewModel: SettingsViewModel,
    open: (String) -> Unit,
) {
    block {
        SettingsGroup("Automation") {
            SettingsToggle(
                "Activity detection",
                if (state.runningAction == "activity") "Updating…" else "Classify driving and idle time",
                state.activityDetectionEnabled,
                "settings_activity_switch",
                { if (it) viewModel.registerActivityTransitions() else viewModel.unregisterActivityTransitions() },
                enabled = state.runningAction == null && (state.activityDetectionEnabled || permissions.state.activity),
            )
        }
    }
    if (!state.privacyDisclosureAccepted) {
        block {
            SettingsLink("Local automation consent", "Review privacy settings", "settings_consent", { open("privacy") }, warning = true)
        }
    }
    block { PermissionRows(permissions) }
    block {
        SettingsNote("Background location detects arrivals and departures while the app is closed.")
        TextButton(onClick = permissions.appSettings) { Text("Open Android app settings") }
    }
}

private fun LazyListScope.notificationSettings(
    state: SettingsUiState,
    permissions: SettingsPermissionActions,
    viewModel: SettingsViewModel,
) {
    block { NotificationsCard(state, viewModel) }
    if (!permissions.state.notifications) {
        block {
            SettingsGroup("Android permission") {
                SettingsLink(
                    "Notifications are blocked",
                    "Allow notifications in Android",
                    "settings_permission_notifications",
                    permissions.notifications,
                    warning = true,
                )
            }
            TextButton(onClick = permissions.appSettings) { Text("Open Android app settings") }
        }
    }
}
