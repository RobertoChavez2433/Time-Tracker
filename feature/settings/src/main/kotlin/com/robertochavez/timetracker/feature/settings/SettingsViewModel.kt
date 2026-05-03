package com.robertochavez.timetracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import com.robertochavez.timetracker.core.common.repository.LocalDataResetter
import com.robertochavez.timetracker.core.common.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.location.activity.ActivityTransitionRegistrar
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.logging.warn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val workScheduleRepository: WorkScheduleRepository,
    private val payPeriodSettingsRepository: PayPeriodSettingsRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val localDataResetter: LocalDataResetter,
    private val activityTransitionRegistrar: ActivityTransitionRegistrar,
    private val logger: AppLogger,
) : ViewModel() {
    private val editedAnchorDate = MutableStateFlow<String?>(null)
    private val statusMessage = MutableStateFlow("")

    val uiState: StateFlow<SettingsUiState> = combine(
        workScheduleRepository.observeWorkSchedule(),
        payPeriodSettingsRepository.observeSettings(),
        appSettingsRepository.settings,
        editedAnchorDate,
        statusMessage,
    ) { schedule, payPeriod, appSettings, editedAnchor, status ->
        SettingsUiState(
            anchorDate = editedAnchor ?: payPeriod.biweeklyAnchorStartDate.toString(),
            workdays = DayOfWeek.entries.map { day ->
                WorkdayUiModel(day.name, day in schedule.trackableDays)
            },
            minimalActiveNotificationEnabled = appSettings.minimalActiveNotificationEnabled,
            liveTimerNotificationEnabled = appSettings.liveTimerNotificationEnabled,
            privacyDisclosureAccepted = appSettings.privacyDisclosureAccepted,
            statusMessage = status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updateAnchorDate(value: String) {
        editedAnchorDate.value = value
    }

    fun saveAnchorDate() {
        viewModelScope.launch {
            runCatching {
                LocalDate.parse(uiState.value.anchorDate)
            }.onSuccess { date ->
                payPeriodSettingsRepository.setSettings(PayPeriodSettings(date))
                editedAnchorDate.value = null
                statusMessage.value = "Biweekly anchor saved."
                logger.info(LogCategory.SETTINGS, "Biweekly anchor saved", mapOf("anchorDate" to date.toString()))
            }.onFailure {
                statusMessage.value = "Use YYYY-MM-DD for the biweekly anchor."
                logger.info(LogCategory.SETTINGS, "Biweekly anchor save rejected")
            }
        }
    }

    fun setDayTrackable(dayName: String, trackable: Boolean) {
        viewModelScope.launch {
            val day = DayOfWeek.valueOf(dayName)
            val current = workScheduleRepository.getWorkSchedule()
            val updatedDays = if (trackable) {
                current.trackableDays + day
            } else {
                current.trackableDays - day
            }
            workScheduleRepository.setWorkSchedule(WorkSchedule(updatedDays))
            logger.info(LogCategory.SETTINGS, "Workday setting changed", mapOf("day" to day.name, "trackable" to trackable))
        }
    }

    fun setMinimalActiveNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setMinimalActiveNotificationEnabled(enabled)
        }
    }

    fun setLiveTimerNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setLiveTimerNotificationEnabled(enabled)
        }
    }

    fun setPrivacyDisclosureAccepted(accepted: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setPrivacyDisclosureAccepted(accepted)
        }
    }

    fun registerActivityTransitions() {
        viewModelScope.launch {
            runCatching {
                activityTransitionRegistrar.registerDriveAndIdleTransitions()
            }.onSuccess {
                statusMessage.value = "Activity detection enabled."
                logger.info(LogCategory.ACTIVITY, "Activity detection enabled from settings")
            }.onFailure { error ->
                logger.warn(LogCategory.ACTIVITY, "Activity detection enable failed", error = error)
                statusMessage.value = error.message ?: "Activity detection could not be enabled."
            }
        }
    }

    fun unregisterActivityTransitions() {
        viewModelScope.launch {
            runCatching {
                activityTransitionRegistrar.unregisterDriveAndIdleTransitions()
            }.onSuccess {
                statusMessage.value = "Activity detection disabled."
                logger.info(LogCategory.ACTIVITY, "Activity detection disabled from settings")
            }
        }
    }

    fun onForegroundPermissionResult(result: Map<String, Boolean>) {
        val grantedCount = result.values.count { it }
        statusMessage.value = "$grantedCount of ${result.size} foreground tracking permissions granted."
        logger.info(LogCategory.SETTINGS, "Foreground permission result", mapOf("grantedCount" to grantedCount, "total" to result.size))
    }

    fun onBackgroundPermissionResult(granted: Boolean) {
        statusMessage.value = if (granted) {
            "Background location granted."
        } else {
            "Background location was not granted. Automatic home enter and exit detection may not work while the app is closed."
        }
        logger.info(LogCategory.SETTINGS, "Background location permission result", mapOf("granted" to granted))
    }

    fun onBackgroundLocationSettingsOpened() {
        statusMessage.value = "In Android settings, choose location and enable Allow all the time for automatic tracking."
        logger.info(LogCategory.SETTINGS, "Background location settings opened")
    }

    fun deleteAllLocalData() {
        viewModelScope.launch {
            runCatching {
                localDataResetter.deleteAllLocalData()
            }.onSuccess {
                statusMessage.value = "Local time tracking data deleted."
                logger.info(LogCategory.SETTINGS, "Local data delete completed")
            }.onFailure { error ->
                logger.warn(LogCategory.SETTINGS, "Local data delete failed", error = error)
                statusMessage.value = error.message ?: "Local data could not be deleted."
            }
        }
    }
}

data class SettingsUiState(
    val anchorDate: String = LocalDate.now().toString(),
    val workdays: List<WorkdayUiModel> = DayOfWeek.entries.map { WorkdayUiModel(it.name, it in WorkSchedule.DEFAULT_TRACKABLE_DAYS) },
    val minimalActiveNotificationEnabled: Boolean = false,
    val liveTimerNotificationEnabled: Boolean = false,
    val privacyDisclosureAccepted: Boolean = false,
    val statusMessage: String = "",
)

data class WorkdayUiModel(val name: String, val trackable: Boolean)
