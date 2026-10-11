package com.robertochavez.timetracker.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Sync

internal fun LazyListScope.settingsIndex(state: SettingsUiState, permissions: SettingsPermissions, open: (String) -> Unit) {
    item {
        SettingsGroup("Tracking") {
            SettingsLink(
                "Tracking & permissions",
                permissions.summary(state),
                "settings_tracking",
                { open("tracking") },
                Icons.Outlined.MyLocation,
                warning = !permissions.trackingGranted || !state.privacyDisclosureAccepted,
            )
            SettingsDivider()
            SettingsLink("Work schedule", state.scheduleSummary, "settings_schedule", { open("schedule") }, Icons.Outlined.CalendarMonth)
            SettingsDivider()
            SettingsLink(
                "Notifications",
                state.notificationSummary,
                "settings_notifications",
                {
                    open("notifications")
                },
                Icons.Outlined.Notifications,
            )
        }
    }
    item {
        SettingsGroup("Connections & data") {
            SettingsLink("Work Hub", state.hubConnectionLabel, "settings_hub", { open("hub") }, Icons.Outlined.Sync)
            SettingsDivider()
            SettingsLink("Backup & transfer", "Export or restore your history", "settings_backup", {
                open("backup")
            }, Icons.Outlined.Backup)
            SettingsDivider()
            SettingsLink(
                "Privacy & local data",
                "Data sharing and deletion",
                "settings_privacy",
                { open("privacy") },
                Icons.Outlined.PrivacyTip,
            )
        }
    }
}

internal fun settingsTitle(section: String?): String = when (section) {
    "tracking" -> "Tracking & permissions"
    "schedule" -> "Work schedule"
    "notifications" -> "Notifications"
    "hub" -> "Work Hub"
    "hub_setup" -> "Set up Work Hub"
    "hub_checks" -> "Connection checks"
    "hub_advanced" -> "Advanced & troubleshooting"
    "hub_fallback" -> "Notion fallback"
    "backup" -> "Backup & transfer"
    "backup_export" -> "Create backup"
    "backup_import" -> "Restore backup"
    "privacy" -> "Privacy & local data"
    else -> "Settings"
}
