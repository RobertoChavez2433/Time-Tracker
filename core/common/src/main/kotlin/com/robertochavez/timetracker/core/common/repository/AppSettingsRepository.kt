package com.robertochavez.timetracker.core.common.repository

import com.robertochavez.timetracker.core.common.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setMinimalActiveNotificationEnabled(enabled: Boolean)

    suspend fun setLiveTimerNotificationEnabled(enabled: Boolean)

    suspend fun setPrivacyDisclosureAccepted(accepted: Boolean)

    suspend fun setActivityDetectionEnabled(enabled: Boolean)

    suspend fun resetSettings()
}
