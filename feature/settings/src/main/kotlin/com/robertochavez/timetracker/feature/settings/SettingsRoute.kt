package com.robertochavez.timetracker.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerCard
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSectionTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerStatusText

@Composable
fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
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

    TimeTrackerScreen(modifier = modifier) {
        item {
            TimeTrackerScreenTitle(
                title = "Settings",
                subtitle = "Permissions, schedule, notifications, and local data.",
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
            TimeTrackerSectionTitle("Workdays")
        }
        items(state.workdays, key = { it.name }) { day ->
            WorkdayRow(day = day, onTrackableChange = { viewModel.setDayTrackable(day.name, it) })
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
            TimeTrackerCard {
                SettingSwitch(
                    label = "I understand background location and activity recognition are used only for local timesheet automation.",
                    checked = state.privacyDisclosureAccepted,
                    onCheckedChange = viewModel::setPrivacyDisclosureAccepted,
                )
            }
        }
        item {
            LocalDataCard(onDeleteLocalData = viewModel::deleteAllLocalData)
        }
        if (state.statusMessage.isNotBlank()) {
            item {
                TimeTrackerStatusText(state.statusMessage)
            }
        }
    }
}

@Composable
private fun PermissionCard(
    backgroundPermissionLabel: String,
    onRequestForegroundPermissions: () -> Unit,
    onEnableBackgroundLocation: () -> Unit,
) {
    TimeTrackerCard {
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        TimeTrackerMutedText(
            "Automatic tracking uses precise foreground location to set home, background location to receive " +
                "home geofence events while the app is closed, and activity recognition to classify away time.",
        )
        TimeTrackerPrimaryButton(text = "Request Foreground Permissions", onClick = onRequestForegroundPermissions)
        TimeTrackerMutedText(
            "For background location, choose $backgroundPermissionLabel so home enter " +
                "and exit events can be delivered outside the app.",
        )
        TimeTrackerPrimaryButton(text = "Enable Background Location", onClick = onEnableBackgroundLocation)
    }
}

@Composable
private fun AutomationCard(onEnableActivityDetection: () -> Unit, onDisableActivityDetection: () -> Unit) {
    TimeTrackerCard {
        Text("Automation", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeTrackerPrimaryButton(text = "Enable Activity Detection", onClick = onEnableActivityDetection)
            TimeTrackerPrimaryButton(text = "Disable", onClick = onDisableActivityDetection)
        }
    }
}

@Composable
private fun PayPeriodCard(anchorDate: String, onAnchorDateChange: (String) -> Unit, onSaveAnchorDate: () -> Unit) {
    TimeTrackerCard {
        Text("Pay Period", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = anchorDate,
            onValueChange = onAnchorDateChange,
            label = { Text("Biweekly anchor date") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TimeTrackerPrimaryButton(text = "Save Anchor", onClick = onSaveAnchorDate)
    }
}

@Composable
private fun WorkdayRow(day: WorkdayUiModel, onTrackableChange: (Boolean) -> Unit) {
    TimeTrackerCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(day.name)
            Switch(
                checked = day.trackable,
                onCheckedChange = onTrackableChange,
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
    TimeTrackerCard {
        Text("Notifications", style = MaterialTheme.typography.titleMedium)
        SettingSwitch(
            label = "Minimal active notification",
            checked = minimalActiveNotificationEnabled,
            onCheckedChange = onMinimalActiveNotificationChange,
        )
        SettingSwitch(
            label = "Live timer notification",
            checked = liveTimerNotificationEnabled,
            onCheckedChange = onLiveTimerNotificationChange,
        )
    }
}

@Composable
private fun LocalDataCard(onDeleteLocalData: () -> Unit) {
    TimeTrackerCard {
        Text("Local Data", style = MaterialTheme.typography.titleMedium)
        TimeTrackerMutedText(
            "Delete saved home location, sessions, activity intervals, mileage, schedules, and app preferences on this device.",
        )
        TimeTrackerPrimaryButton(text = "Delete Local Data", onClick = onDeleteLocalData)
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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
