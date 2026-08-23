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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.common.model.PayPeriodCadence
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
import com.robertochavez.timetracker.core.sync.HubTransportKind

@Composable
@Suppress("LongMethod")
fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var notionToken by rememberSaveable { mutableStateOf("") }
    var dailyLogId by rememberSaveable { mutableStateOf("") }
    var projectsId by rememberSaveable { mutableStateOf("") }
    var timeRecordsId by rememberSaveable { mutableStateOf("") }
    var backupPassphrase by rememberSaveable { mutableStateOf("") }
    val outboxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::setHubOutboxTree)
    }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let { viewModel.exportEncryptedBackup(it, backupPassphrase) } }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importEncryptedBackup(it, backupPassphrase) }
    }
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
            WorkdaysSection(workdays = state.workdays, onTrackableChange = viewModel::setDayTrackable)
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
                cadence = state.payPeriodCadence,
                preview = state.payPeriodPreview,
                onAnchorDateChange = viewModel::updateAnchorDate,
                onCadenceSelected = viewModel::selectPayPeriodCadence,
                onSaveAnchorDate = viewModel::saveAnchorDate,
            )
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
            WorkHubSyncSection(
                state = state,
                notionToken = notionToken,
                dailyLogId = dailyLogId,
                projectsId = projectsId,
                timeRecordsId = timeRecordsId,
                onActivationDateChange = viewModel::setHubActivationDate,
                onChooseOutbox = { outboxLauncher.launch(null) },
                onVerifyProvider = viewModel::verifyHubProvider,
                onTransportChange = viewModel::setHubTransport,
                onNotionTokenChange = { notionToken = it },
                onDailyLogIdChange = { dailyLogId = it },
                onProjectsIdChange = { projectsId = it },
                onTimeRecordsIdChange = { timeRecordsId = it },
                onSaveFallback = { viewModel.saveNotionFallback(notionToken, dailyLogId, projectsId, timeRecordsId) },
                onSyncNow = viewModel::syncHubNow,
                onFullResync = viewModel::fullResyncHub,
            )
        }
        item {
            LocalDataCard(onDeleteLocalData = { showDeleteConfirmation = true })
        }
        item {
            EncryptedTransferSection(
                passphrase = backupPassphrase,
                onPassphraseChange = { backupPassphrase = it },
                onExport = { backupExportLauncher.launch("time-tracker-backup.ttbackup") },
                onImport = { backupImportLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*")) },
            )
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
private fun EncryptedTransferSection(
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    TimeTrackerSettingSection(
        title = "Encrypted phone transfer",
        subtitle = "Move local tracking history to an empty Time Tracker installation. " +
            "Tokens, folder permissions, and credentials are excluded.",
    ) {
        OutlinedTextField(
            value = passphrase,
            onValueChange = onPassphraseChange,
            label = { Text("Backup passphrase (10+ characters)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag(TimeTrackerTestTags.SETTINGS_BACKUP_PASSPHRASE_FIELD),
        )
        TimeTrackerSecondaryButton(
            text = "Export encrypted backup",
            onClick = onExport,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_BACKUP_EXPORT_BUTTON),
        )
        TimeTrackerSecondaryButton(
            text = "Import into empty app",
            onClick = onImport,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_BACKUP_IMPORT_BUTTON),
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun WorkHubSyncSection(
    state: SettingsUiState,
    notionToken: String,
    dailyLogId: String,
    projectsId: String,
    timeRecordsId: String,
    onActivationDateChange: (String) -> Unit,
    onChooseOutbox: () -> Unit,
    onVerifyProvider: () -> Unit,
    onTransportChange: (HubTransportKind) -> Unit,
    onNotionTokenChange: (String) -> Unit,
    onDailyLogIdChange: (String) -> Unit,
    onProjectsIdChange: (String) -> Unit,
    onTimeRecordsIdChange: (String) -> Unit,
    onSaveFallback: () -> Unit,
    onSyncNow: () -> Unit,
    onFullResync: () -> Unit,
) {
    var activationInput by rememberSaveable(state.hubActivationDate) { mutableStateOf(state.hubActivationDate) }
    TimeTrackerSettingSection(
        title = "Work Hub sync",
        subtitle = "Exports date and project summaries only. Location coordinates, addresses, routes, and home data never leave the app.",
    ) {
        OutlinedTextField(
            value = activationInput,
            onValueChange = { activationInput = it },
            label = { Text("Activation date (YYYY-MM-DD)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(TimeTrackerTestTags.SETTINGS_HUB_ACTIVATION_FIELD),
        )
        TimeTrackerSecondaryButton(
            text = "Save activation date",
            onClick = { onActivationDateChange(activationInput) },
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_HUB_SAVE_ACTIVATION_BUTTON),
        )
        TimeTrackerSecondaryButton(
            text = if (state.hubOutboxConfigured) "Change Work Hub Outbox" else "Choose Work Hub Outbox",
            onClick = onChooseOutbox,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_HUB_OUTBOX_BUTTON),
        )
        TimeTrackerSecondaryButton(
            text = "Test OneDrive provider",
            onClick = onVerifyProvider,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_HUB_VERIFY_PROVIDER_BUTTON),
        )
        Text(
            if (state.hubProviderVerified) "Provider test passed" else "Provider not verified",
            color = if (state.hubProviderVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_HUB_PROVIDER_STATUS),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(com.robertochavez.timetracker.core.designsystem.TimeTrackerSpacing.Small)) {
            FilterChip(
                selected = state.hubTransport == HubTransportKind.DOCUMENT_TREE,
                onClick = { onTransportChange(HubTransportKind.DOCUMENT_TREE) },
                label = { Text("OneDrive outbox") },
            )
            FilterChip(
                selected = state.hubTransport == HubTransportKind.DIRECT_NOTION,
                onClick = { onTransportChange(HubTransportKind.DIRECT_NOTION) },
                label = { Text("Direct Notion fallback") },
            )
        }
        if (state.hubTransport == HubTransportKind.DIRECT_NOTION) {
            NotionFallbackField(notionToken, onNotionTokenChange, "Notion integration token", secret = true)
            NotionFallbackField(dailyLogId, onDailyLogIdChange, "Daily Log data source ID")
            NotionFallbackField(projectsId, onProjectsIdChange, "Projects data source ID")
            NotionFallbackField(timeRecordsId, onTimeRecordsIdChange, "Time Records data source ID")
            TimeTrackerSecondaryButton(text = "Save encrypted fallback", onClick = onSaveFallback)
        }
        Text(state.hubSyncStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TimeTrackerPrimaryButton(
            text = "Sync Now",
            onClick = onSyncNow,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_HUB_SYNC_NOW_BUTTON),
        )
        TimeTrackerQuietButton(text = "Full Resync from activation date", onClick = onFullResync)
    }
}

@Composable
private fun NotionFallbackField(value: String, onValueChange: (String) -> Unit, label: String, secret: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
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
            text = "Disable Activity Detection",
            onClick = onDisableActivityDetection,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_DISABLE_ACTIVITY_BUTTON),
        )
    }
}

@Composable
private fun PayPeriodCard(
    anchorDate: String,
    cadence: PayPeriodCadence,
    preview: String,
    onAnchorDateChange: (String) -> Unit,
    onCadenceSelected: (PayPeriodCadence) -> Unit,
    onSaveAnchorDate: () -> Unit,
) {
    TimeTrackerSettingSection(
        title = "Pay period",
        subtitle = "Choose how often a period starts. The date is the inclusive first day of a known period.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(com.robertochavez.timetracker.core.designsystem.TimeTrackerSpacing.Small),
        ) {
            FilterChip(
                selected = cadence == PayPeriodCadence.WEEKLY,
                onClick = { onCadenceSelected(PayPeriodCadence.WEEKLY) },
                label = { Text("Weekly") },
                modifier = Modifier
                    .weight(1f)
                    .testTag(TimeTrackerTestTags.SETTINGS_WEEKLY_CADENCE_BUTTON),
            )
            FilterChip(
                selected = cadence == PayPeriodCadence.BIWEEKLY,
                onClick = { onCadenceSelected(PayPeriodCadence.BIWEEKLY) },
                label = { Text("Biweekly") },
                modifier = Modifier
                    .weight(1f)
                    .testTag(TimeTrackerTestTags.SETTINGS_BIWEEKLY_CADENCE_BUTTON),
            )
        }
        OutlinedTextField(
            value = anchorDate,
            onValueChange = onAnchorDateChange,
            label = { Text("Known period start (YYYY-MM-DD)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TimeTrackerTestTags.SETTINGS_ANCHOR_DATE_FIELD),
        )
        Text(
            text = "Current period: $preview",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_PAY_PERIOD_PREVIEW),
        )
        TimeTrackerPrimaryButton(
            text = "Save Pay Period",
            onClick = onSaveAnchorDate,
            modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_SAVE_ANCHOR_BUTTON),
        )
    }
}

@Composable
private fun WorkdaysSection(workdays: List<WorkdayUiModel>, onTrackableChange: (String, Boolean) -> Unit) {
    TimeTrackerSettingSection(
        title = "Tracked days",
        subtitle = "Automatic sessions can start only on selected days.",
    ) {
        if (workdays.none { it.trackable }) {
            Text(
                "Automatic tracking is paused until at least one day is selected.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        workdays.forEach { day ->
            TimeTrackerSettingRow(label = day.name.lowercase().replaceFirstChar(Char::uppercase)) {
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
