package com.robertochavez.timetracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.database.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.database.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.datastore.SettingsDataStore
import com.robertochavez.timetracker.core.location.activity.ActivityTransitionRegistrar
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
    private val settingsDataStore: SettingsDataStore,
    private val activityTransitionRegistrar: ActivityTransitionRegistrar,
) : ViewModel() {
    private val editedAnchorDate = MutableStateFlow<String?>(null)
    private val statusMessage = MutableStateFlow("")

    val uiState: StateFlow<SettingsUiState> = combine(
        workScheduleRepository.observeWorkSchedule(),
        payPeriodSettingsRepository.observeSettings(),
        settingsDataStore.settings,
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
            }.onFailure {
                statusMessage.value = "Use YYYY-MM-DD for the biweekly anchor."
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
        }
    }

    fun setMinimalActiveNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setMinimalActiveNotificationEnabled(enabled)
        }
    }

    fun setLiveTimerNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setLiveTimerNotificationEnabled(enabled)
        }
    }

    fun setPrivacyDisclosureAccepted(accepted: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setPrivacyDisclosureAccepted(accepted)
        }
    }

    fun registerActivityTransitions() {
        viewModelScope.launch {
            runCatching {
                activityTransitionRegistrar.registerDriveAndIdleTransitions()
            }.onSuccess {
                statusMessage.value = "Activity detection enabled."
            }.onFailure { error ->
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
            }
        }
    }

    fun onPermissionResult(result: Map<String, Boolean>) {
        val grantedCount = result.values.count { it }
        statusMessage.value = "$grantedCount of ${result.size} tracking permissions granted."
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

data class WorkdayUiModel(
    val name: String,
    val trackable: Boolean,
)
