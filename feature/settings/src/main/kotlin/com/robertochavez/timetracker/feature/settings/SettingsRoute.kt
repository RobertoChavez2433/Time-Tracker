package com.robertochavez.timetracker.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerDestructiveButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerQuietButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSecondaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSettingRow
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSettingSection
import com.robertochavez.timetracker.core.designsystem.TimeTrackerStatusText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@Composable
fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { viewModel.onForegroundPermissionResult(it) },
    )
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.onBackgroundPermissionResult(it) },
    )
    val backgroundSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { viewModel.onBackgroundLocationSettingsOpened() },
    )

    TimeTrackerScreen(modifier = modifier, testTag = TimeTrackerTestTags.SETTINGS_SCREEN) {
        item {
            TimeTrackerScreenTitle(
                title = "Settings",
                subtitle = "Tracking rules, notifications, and local data controls.",
            )
        }
        item {
            PermissionCard(
                backgroundPermissionLabel = backgroundPermissionOptionLabel(context),
                onRequestForegroundPermissions = {
                    foregroundPermissionLauncher.launch(foregroundTrackingPermissions())
                },
                onEnableBackgroundLocation = {
                    if (usesBackgroundLocationSettingsFlow()) {
                        backgroundSettingsLauncher.launch(backgroundLocationSettingsIntent(context.packageName))
                    } else {
                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                },
            )
        }
        item {
            AutomationCard(
                onEnableActivityDetection = viewModel::registerActivityTransitions,
                onDisableActivityDetection = viewModel::unregisterActivityTransitions,
            )
        }
        item {
            PayPeriodCard(
                anchorDate = state.anchorDate,
                onAnchorDateChange = viewModel::updateAnchorDate,
                onSaveAnchorDate = viewModel::saveAnchorDate,
            )
        }
        item {
            WorkdaysSection(workdays = state.workdays, onTrackableChange = viewModel::setDayTrackable)
        }
        item {
            NotificationsCard(
                minimalActiveNotificationEnabled = state.minimalActiveNotificationEnabled,
                liveTimerNotificationEnabled = state.liveTimerNotificationEnabled,
                onMinimalActiveNotificationChange = viewModel::setMinimalActiveNotificationEnabled,
                onLiveTimerNotificationChange = viewModel::setLiveTimerNotificationEnabled,
            )
        }
        item {
            PrivacySection(state.privacyDisclosureAccepted, viewModel::setPrivacyDisclosureAccepted)
        }
        item {
            LocalDataCard(onDeleteLocalData = { showDeleteConfirmation = true })
        }
        if (state.statusMessage.isNotBlank()) {
            item {
                TimeTrackerStatusText(state.statusMessage)
            }
        }
    }

    if (showDeleteConfirmation) {
        DeleteLocalDataConfirmationDialog(
            onCancel = { showDeleteConfirmation = false },
            onConfirm = {
                showDeleteConfirmation = false
                viewModel.deleteAllLocalData()
            },
        )
    }
}

@Composable
private fun PermissionCard(
    backgroundPermissionLabel: String,
    onRequestForegroundPermissions: () -> Unit,
    onEnableBackgroundLocation: () -> Unit,
) {
    TimeTrackerSettingSection(title = "Permissions", subtitle = "Grant foreground access first, then enable background location.") {
        TimeTrackerPrimaryButton(
            text = "Request Foreground Permissions",
            onClick = onRequestForegroundPermissions,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_REQUEST_FOREGROUND_BUTTON),
        )
        Text(
            "For background location, choose $backgroundPermissionLabel in Android settings.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        TimeTrackerSecondaryButton(
            text = "Enable Background Location",
            onClick = onEnableBackgroundLocation,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_ENABLE_BACKGROUND_BUTTON),
        )
    }
}

@Composable
private fun AutomationCard(onEnableActivityDetection: () -> Unit, onDisableActivityDetection: () -> Unit) {
    TimeTrackerSettingSection(title = "Automation", subtitle = "Activity detection classifies drive and idle time.") {
        TimeTrackerPrimaryButton(
            text = "Enable Activity Detection",
            onClick = onEnableActivityDetection,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_ENABLE_ACTIVITY_BUTTON),
        )
        TimeTrackerQuietButton(
            text = "Disable",
            onClick = onDisableActivityDetection,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_DISABLE_ACTIVITY_BUTTON),
        )
    }
}

@Composable
private fun PayPeriodCard(anchorDate: String, onAnchorDateChange: (String) -> Unit, onSaveAnchorDate: () -> Unit) {
    TimeTrackerSettingSection(title = "Pay period") {
        OutlinedTextField(
            value = anchorDate,
            onValueChange = onAnchorDateChange,
            label = { Text("Biweekly anchor date") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TimeTrackerTestTags.SETTINGS_ANCHOR_DATE_FIELD),
        )
        TimeTrackerPrimaryButton(
            text = "Save Anchor",
            onClick = onSaveAnchorDate,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_SAVE_ANCHOR_BUTTON),
        )
    }
}

@Composable
private fun WorkdaysSection(workdays: List<WorkdayUiModel>, onTrackableChange: (String, Boolean) -> Unit) {
    TimeTrackerSettingSection(title = "Workdays") {
        workdays.forEach { day ->
            TimeTrackerSettingRow(label = day.name) {
                Switch(
                    checked = day.trackable,
                    onCheckedChange = { onTrackableChange(day.name, it) },
                    modifier = Modifier.testTag(TimeTrackerTestTags.workdaySwitch(day.name)),
                )
            }
        }
    }
}

@Composable
private fun PrivacySection(accepted: Boolean, onAcceptedChange: (Boolean) -> Unit) {
    TimeTrackerSettingSection(title = "Privacy") {
        TimeTrackerSettingRow(
            label = "Local-only automation",
            supportingText = "Background location and activity recognition are used only for local timesheet automation.",
        ) {
            Switch(
                checked = accepted,
                onCheckedChange = onAcceptedChange,
                modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_PRIVACY_DISCLOSURE_SWITCH),
            )
        }
    }
}

@Composable
private fun NotificationsCard(
    minimalActiveNotificationEnabled: Boolean,
    liveTimerNotificationEnabled: Boolean,
    onMinimalActiveNotificationChange: (Boolean) -> Unit,
    onLiveTimerNotificationChange: (Boolean) -> Unit,
) {
    TimeTrackerSettingSection(title = "Notifications") {
        SettingSwitch(
            label = "Minimal active notification",
            checked = minimalActiveNotificationEnabled,
            onCheckedChange = onMinimalActiveNotificationChange,
            testTag = TimeTrackerTestTags.SETTINGS_MINIMAL_NOTIFICATION_SWITCH,
        )
        SettingSwitch(
            label = "Live timer notification",
            checked = liveTimerNotificationEnabled,
            onCheckedChange = onLiveTimerNotificationChange,
            testTag = TimeTrackerTestTags.SETTINGS_LIVE_TIMER_NOTIFICATION_SWITCH,
        )
    }
}

@Composable
private fun LocalDataCard(onDeleteLocalData: () -> Unit) {
    TimeTrackerSettingSection(title = "Local data", subtitle = "Reset this device after confirmation.") {
        TimeTrackerSecondaryButton(
            text = "Delete Local Data",
            onClick = onDeleteLocalData,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_DELETE_LOCAL_DATA_BUTTON),
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, testTag: String) {
    TimeTrackerSettingRow(label = label) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(testTag))
    }
}

@Composable
private fun DeleteLocalDataConfirmationDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                "Delete local data?",
                modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_DELETE_CONFIRM_DIALOG),
            )
        },
        text = { Text("This clears saved locations, sessions, mileage, workdays, pay-period settings, and preferences on this device.") },
        dismissButton = {
            TimeTrackerQuietButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_DELETE_CONFIRM_CANCEL_BUTTON),
            )
        },
        confirmButton = {
            TimeTrackerDestructiveButton(
                text = "Delete",
                onClick = onConfirm,
                modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_DELETE_CONFIRM_BUTTON),
            )
        },
    )
}

private fun foregroundTrackingPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Manifest.permission.ACTIVITY_RECOGNITION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun usesBackgroundLocationSettingsFlow(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

private fun backgroundPermissionOptionLabel(context: Context): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    context.packageManager.backgroundPermissionOptionLabel.toString()
} else {
    "Allow all the time"
}

private fun backgroundLocationSettingsIntent(packageName: String): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", packageName, null),
)
