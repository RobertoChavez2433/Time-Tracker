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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
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

    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
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
                Text("Workdays", style = MaterialTheme.typography.titleLarge)
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
                SettingSwitch(
                    label = "I understand background location and activity recognition are used only for local timesheet automation.",
                    checked = state.privacyDisclosureAccepted,
                    onCheckedChange = viewModel::setPrivacyDisclosureAccepted,
                )
            }
            item {
                LocalDataCard(onDeleteLocalData = viewModel::deleteAllLocalData)
            }
            if (state.statusMessage.isNotBlank()) {
                item {
                    Text(state.statusMessage, color = MaterialTheme.colorScheme.primary)
                }
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            Text(
                "Automatic tracking uses precise foreground location to set home, background location to receive " +
                    "home geofence events while the app is closed, and activity recognition to classify away time.",
            )
            Button(onClick = onRequestForegroundPermissions) {
                Text("Request Foreground Permissions")
            }
            Text(
                "For background location, choose $backgroundPermissionLabel so home enter " +
                    "and exit events can be delivered outside the app.",
            )
            Button(onClick = onEnableBackgroundLocation) {
                Text("Enable Background Location")
            }
        }
    }
}

@Composable
private fun AutomationCard(
    onEnableActivityDetection: () -> Unit,
    onDisableActivityDetection: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Automation", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEnableActivityDetection) {
                    Text("Enable Activity Detection")
                }
                Button(onClick = onDisableActivityDetection) {
                    Text("Disable")
                }
            }
        }
    }
}

@Composable
private fun PayPeriodCard(
    anchorDate: String,
    onAnchorDateChange: (String) -> Unit,
    onSaveAnchorDate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Pay Period", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = anchorDate,
                onValueChange = onAnchorDateChange,
                label = { Text("Biweekly anchor date") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onSaveAnchorDate) {
                Text("Save Anchor")
            }
        }
    }
}

@Composable
private fun WorkdayRow(
    day: WorkdayUiModel,
    onTrackableChange: (Boolean) -> Unit,
) {
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

@Composable
private fun NotificationsCard(
    minimalActiveNotificationEnabled: Boolean,
    liveTimerNotificationEnabled: Boolean,
    onMinimalActiveNotificationChange: (Boolean) -> Unit,
    onLiveTimerNotificationChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
}

@Composable
private fun LocalDataCard(
    onDeleteLocalData: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Local Data", style = MaterialTheme.typography.titleMedium)
            Text("Delete saved home location, sessions, activity intervals, mileage, schedules, and app preferences on this device.")
            Button(onClick = onDeleteLocalData) {
                Text("Delete Local Data")
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
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
