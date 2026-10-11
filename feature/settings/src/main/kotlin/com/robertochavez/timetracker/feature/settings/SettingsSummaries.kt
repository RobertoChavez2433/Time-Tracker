package com.robertochavez.timetracker.feature.settings

import com.robertochavez.timetracker.core.common.model.PayPeriodCadence
import com.robertochavez.timetracker.core.sync.HubProviderGateState
import com.robertochavez.timetracker.core.sync.HubTransportKind
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

internal val SettingsUiState.scheduleSummary: String
    get() {
        val days = workdays.filter { it.trackable }.map { DayOfWeek.valueOf(it.name) }
        val dayLabel = when (days) {
            emptyList<DayOfWeek>() -> "No days selected"
            DayOfWeek.entries -> "Every day"
            DayOfWeek.entries.take(5) -> "Mon–Fri"
            else -> days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        }
        return "$dayLabel · ${if (payPeriodCadence == PayPeriodCadence.WEEKLY) "Weekly" else "Every 2 weeks"}"
    }

internal val SettingsUiState.notificationSummary: String
    get() = "Compact status ${if (minimalActiveNotificationEnabled) "on" else "off"} · " +
        "Live timer ${if (liveTimerNotificationEnabled) "on" else "off"}"

internal val SettingsUiState.hubFallbackAllowed: Boolean
    get() = hubProviderGateState == HubProviderGateState.BASIC_FAILED || hubProviderGateState == HubProviderGateState.FULL_FAILED

internal val SettingsUiState.hubReady: Boolean
    get() = hubActivationDate.isNotBlank() &&
        when (hubTransport) {
            HubTransportKind.DOCUMENT_TREE -> hubOutboxConfigured && hubProviderGateState == HubProviderGateState.FULL_PASSED
            HubTransportKind.DIRECT_NOTION -> hubFallbackAllowed && hubFallbackConfigured
        }

internal val SettingsUiState.hubConnectionLabel: String
    get() = when {
        hubReady && hubFailedCount > 0 -> "$hubFailedCount failed sync records"
        hubReady -> if (hubTransport == HubTransportKind.DOCUMENT_TREE) "OneDrive outbox ready" else "Notion fallback configured"
        hubTransport == HubTransportKind.DIRECT_NOTION && !hubFallbackConfigured -> "Fallback setup needed"
        !hubOutboxConfigured -> "Not connected"
        hubActivationDate.isBlank() -> "First sync date needed"
        hubFallbackAllowed -> "Connection check failed"
        else -> "Verification needed"
    }
