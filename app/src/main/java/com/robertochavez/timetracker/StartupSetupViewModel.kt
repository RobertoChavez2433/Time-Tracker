package com.robertochavez.timetracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import com.robertochavez.timetracker.core.location.activity.ActivityTransitionRegistrar
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.logging.warn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StartupSetupViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val activityTransitionRegistrar: ActivityTransitionRegistrar,
    private val logger: AppLogger,
) : ViewModel() {
    val uiState: StateFlow<StartupSetupUiState> = appSettingsRepository.settings
        .map { settings ->
            StartupSetupUiState(
                privacyDisclosureAccepted = settings.privacyDisclosureAccepted,
                minimalActiveNotificationEnabled = settings.minimalActiveNotificationEnabled,
                liveTimerNotificationEnabled = settings.liveTimerNotificationEnabled,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StartupSetupUiState())

    fun acceptStartupSetup() {
        viewModelScope.launch {
            appSettingsRepository.setPrivacyDisclosureAccepted(true)
            logger.info(LogCategory.SETTINGS, "Startup setup accepted")
        }
    }

    fun enableActivityDetection() {
        viewModelScope.launch {
            runCatching {
                activityTransitionRegistrar.registerDriveAndIdleTransitions()
            }.onSuccess {
                logger.info(LogCategory.ACTIVITY, "Activity detection enabled from startup setup")
            }.onFailure { error ->
                logger.warn(LogCategory.ACTIVITY, "Startup activity detection enable failed", error = error)
            }
        }
    }

    fun onForegroundPermissionResult(result: Map<String, Boolean>) {
        logger.info(
            LogCategory.SETTINGS,
            "Startup foreground permission result",
            mapOf("grantedCount" to result.values.count { it }, "total" to result.size),
        )
        enableActivityDetection()
    }

    fun onBackgroundLocationSettingsOpened() {
        logger.info(LogCategory.SETTINGS, "Startup background location settings opened")
    }

    fun onBackgroundPermissionResult(granted: Boolean) {
        logger.info(LogCategory.SETTINGS, "Startup background location permission result", mapOf("granted" to granted))
    }
}

data class StartupSetupUiState(
    val privacyDisclosureAccepted: Boolean = false,
    val minimalActiveNotificationEnabled: Boolean = false,
    val liveTimerNotificationEnabled: Boolean = false,
)
