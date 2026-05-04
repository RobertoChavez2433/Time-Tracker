package com.robertochavez.timetracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags
import com.robertochavez.timetracker.core.location.hasActivityRecognitionPermission
import com.robertochavez.timetracker.core.location.hasBackgroundLocationPermission
import com.robertochavez.timetracker.core.location.hasFineLocationPermission
import com.robertochavez.timetracker.core.location.hasPostNotificationsPermission

@Composable
fun StartupSetupGate(startupSetupViewModel: StartupSetupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val setupState by startupSetupViewModel.uiState.collectAsStateWithLifecycle()
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            permissionRefresh += 1
            startupSetupViewModel.onForegroundPermissionResult(it)
        },
    )
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            permissionRefresh += 1
            startupSetupViewModel.onBackgroundPermissionResult(it)
        },
    )
    val backgroundSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            permissionRefresh += 1
            startupSetupViewModel.onBackgroundLocationSettingsOpened()
        },
    )
    val foregroundReady = permissionRefresh.let {
        context.hasFineLocationPermission() &&
            context.hasActivityRecognitionPermission() &&
            context.hasPostNotificationsPermission()
    }
    val backgroundReady = context.hasBackgroundLocationPermission()
    if (setupState.privacyDisclosureAccepted && foregroundReady && backgroundReady) {
        return
    }

    StartupSetupDialog(
        privacyAccepted = setupState.privacyDisclosureAccepted,
        foregroundReady = foregroundReady,
        backgroundReady = backgroundReady,
        backgroundPermissionLabel = backgroundPermissionOptionLabel(context),
        onEnableForeground = {
            startupSetupViewModel.acceptStartupSetup()
            foregroundPermissionLauncher.launch(startupForegroundPermissions())
        },
        onEnableBackground = {
            if (usesBackgroundLocationSettingsFlow()) {
                backgroundSettingsLauncher.launch(backgroundLocationSettingsIntent(context.packageName))
            } else {
                backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        },
    )
}

@Composable
private fun StartupSetupDialog(
    privacyAccepted: Boolean,
    foregroundReady: Boolean,
    backgroundReady: Boolean,
    backgroundPermissionLabel: String,
    onEnableForeground: () -> Unit,
    onEnableBackground: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                "Finish setup",
                modifier = Modifier.testTag(TimeTrackerTestTags.STARTUP_SETUP_DIALOG),
            )
        },
        text = {
            Column {
                Text(checkLine("Privacy agreement", privacyAccepted))
                Text(checkLine("Precise location", foregroundReady))
                Text(checkLine("Activity and notification permissions", foregroundReady))
                Text(checkLine("Background location: $backgroundPermissionLabel", backgroundReady))
            }
        },
        confirmButton = {
            if (!privacyAccepted || !foregroundReady) {
                TimeTrackerPrimaryButton(
                    text = "Agree and Enable Tracking",
                    onClick = onEnableForeground,
                    modifier = Modifier.testTag(TimeTrackerTestTags.STARTUP_ENABLE_BUTTON),
                )
            } else {
                TimeTrackerPrimaryButton(
                    text = "Enable Background Location",
                    onClick = onEnableBackground,
                    modifier = Modifier.testTag(TimeTrackerTestTags.STARTUP_BACKGROUND_BUTTON),
                )
            }
        },
    )
}

private fun checkLine(label: String, ready: Boolean): String = "${if (ready) "Done" else "Needed"}: $label"

private fun startupForegroundPermissions(): Array<String> = buildList {
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
