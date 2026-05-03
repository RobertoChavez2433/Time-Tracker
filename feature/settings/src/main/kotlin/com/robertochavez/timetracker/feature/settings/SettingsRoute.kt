package com.robertochavez.timetracker.feature.settings

import android.Manifest
import android.os.Build
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { viewModel.onPermissionResult(it) },
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Permissions", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Automatic tracking uses precise and background location for the home geofence. " +
                                "Activity recognition classifies away time as drive, idle, or unclassified.",
                        )
                        Button(
                            onClick = {
                                permissionLauncher.launch(requiredPermissions())
                            },
                        ) {
                            Text("Request Tracking Permissions")
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Automation", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::registerActivityTransitions) {
                                Text("Enable Activity Detection")
                            }
                            Button(onClick = viewModel::unregisterActivityTransitions) {
                                Text("Disable")
                            }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Pay Period", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = state.anchorDate,
                            onValueChange = viewModel::updateAnchorDate,
                            label = { Text("Biweekly anchor date") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = viewModel::saveAnchorDate) {
                            Text("Save Anchor")
                        }
                    }
                }
            }
            item {
                Text("Workdays", style = MaterialTheme.typography.titleLarge)
            }
            items(state.workdays, key = { it.name }) { day ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(day.name)
                    Switch(
                        checked = day.trackable,
                        onCheckedChange = { viewModel.setDayTrackable(day.name, it) },
                    )
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Notifications", style = MaterialTheme.typography.titleMedium)
                        SettingSwitch(
                            label = "Minimal active notification",
                            checked = state.minimalActiveNotificationEnabled,
                            onCheckedChange = viewModel::setMinimalActiveNotificationEnabled,
                        )
                        SettingSwitch(
                            label = "Live timer notification",
                            checked = state.liveTimerNotificationEnabled,
                            onCheckedChange = viewModel::setLiveTimerNotificationEnabled,
                        )
                    }
                }
            }
            item {
                SettingSwitch(
                    label = "I understand background location and activity recognition are used only for local timesheet automation.",
                    checked = state.privacyDisclosureAccepted,
                    onCheckedChange = viewModel::setPrivacyDisclosureAccepted,
                )
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

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Manifest.permission.ACTIVITY_RECOGNITION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
