package com.robertochavez.timetracker.feature.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

internal data class SettingsPermissions(
    val precise: Boolean,
    val background: Boolean,
    val activity: Boolean,
    val notifications: Boolean,
    val locationEnabled: Boolean,
) {
    val trackingGranted get() = precise && background && activity && locationEnabled

    fun summary(state: SettingsUiState): String = when {
        !state.privacyDisclosureAccepted -> "Local automation consent needed"
        !locationEnabled -> "Device location is off"
        !precise -> "Precise location needed"
        !background -> "Background location needed"
        !activity -> "Physical activity permission needed"
        !state.activityDetectionEnabled -> "Activity detection off"
        else -> "Tracking permissions ready"
    }
}

internal data class SettingsPermissionActions(
    val state: SettingsPermissions,
    val location: () -> Unit,
    val background: () -> Unit,
    val activity: () -> Unit,
    val notifications: () -> Unit,
    val deviceLocation: () -> Unit,
    val appSettings: () -> Unit,
)

@Composable
internal fun rememberSettingsPermissions(): SettingsPermissionActions {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readSettingsPermissions(context)) }
    val refresh = { state = readSettingsPermissions(context) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    val appSettings = { settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    return SettingsPermissionActions(
        state,
        location = { request.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
        background = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                appSettings()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                request.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            }
        },
        activity = {
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {
                request.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
            }
        },
        notifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                request.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            } else {
                settings.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        },
        deviceLocation = { settings.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        appSettings = appSettings,
    )
}

private fun readSettingsPermissions(context: Context): SettingsPermissions {
    fun granted(name: String) = context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
    val location = context.getSystemService(LocationManager::class.java)
    return SettingsPermissions(
        precise = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        activity = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACTIVITY_RECOGNITION),
        notifications = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
        locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            location.isLocationEnabled
        } else {
            location.isProviderEnabled(LocationManager.GPS_PROVIDER) || location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        },
    )
}

@Composable
internal fun PermissionRows(actions: SettingsPermissionActions) {
    val state = actions.state
    SettingsGroup("Permissions") {
        PermissionRow("Precise location", state.precise, "Allow precise location", "settings_permission_location", actions.location)
        SettingsDivider()
        PermissionRow(
            "Background location",
            state.background && state.precise,
            "Choose “Allow all the time”",
            "settings_permission_background",
            if (state.precise) actions.background else actions.location,
        )
        SettingsDivider()
        PermissionRow("Physical activity", state.activity, "Allow activity recognition", "settings_permission_activity", actions.activity)
        if (!state.locationEnabled) {
            SettingsDivider()
            SettingsLink(
                "Device location",
                "Turn on location in Android",
                "settings_device_location",
                actions.deviceLocation,
                warning = true,
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, action: String, tag: String, onRequest: () -> Unit) {
    if (granted) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            SettingsRowText(title, "Allowed", Modifier.weight(1f))
            Icon(Icons.Default.Check, contentDescription = null)
        }
    } else {
        SettingsLink(title, action, tag, onRequest, warning = true)
    }
}
