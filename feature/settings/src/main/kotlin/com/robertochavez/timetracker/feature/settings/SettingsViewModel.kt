package com.robertochavez.timetracker.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.domain.payPeriodRangeFor
import com.robertochavez.timetracker.core.common.model.PayPeriodCadence
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
import com.robertochavez.timetracker.core.sync.HubSyncSettings
import com.robertochavez.timetracker.core.sync.HubSyncStatus
import com.robertochavez.timetracker.core.sync.HubTransportKind
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
@Suppress("LargeClass", "TooManyFunctions")
class SettingsViewModel @Inject constructor(
    private val workScheduleRepository: WorkScheduleRepository,
    private val payPeriodSettingsRepository: PayPeriodSettingsRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val localDataResetter: LocalDataResetter,
    private val activityTransitionRegistrar: ActivityTransitionRegistrar,
    private val logger: AppLogger,
    private val workHubSettingsGateway: WorkHubSettingsGateway,
) : ViewModel() {
    constructor(
        workScheduleRepository: WorkScheduleRepository,
        payPeriodSettingsRepository: PayPeriodSettingsRepository,
        appSettingsRepository: AppSettingsRepository,
        localDataResetter: LocalDataResetter,
        activityTransitionRegistrar: ActivityTransitionRegistrar,
        logger: AppLogger,
    ) : this(
        workScheduleRepository,
        payPeriodSettingsRepository,
        appSettingsRepository,
        localDataResetter,
        activityTransitionRegistrar,
        logger,
        NoopWorkHubSettingsGateway,
    )
    private val editedAnchorDate = MutableStateFlow<String?>(null)
    private val editedCadence = MutableStateFlow<PayPeriodCadence?>(null)
    private val statusMessage = MutableStateFlow("")
    private val payPeriodEdits = combine(editedAnchorDate, editedCadence, ::PayPeriodEdits)
    private val syncState = combine(
        statusMessage,
        workHubSettingsGateway.settings,
        workHubSettingsGateway.status,
        ::SyncState,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        workScheduleRepository.observeWorkSchedule(),
        payPeriodSettingsRepository.observeSettings(),
        appSettingsRepository.settings,
        payPeriodEdits,
        syncState,
    ) { schedule, payPeriod, appSettings, edits, sync ->
        val anchorDate = edits.anchorDate ?: payPeriod.anchorStartDate.toString()
        val cadence = edits.cadence ?: payPeriod.cadence
        val preview = runCatching {
            PayPeriodSettings(LocalDate.parse(anchorDate), cadence).payPeriodRangeFor(LocalDate.now())
        }.getOrNull()
        SettingsUiState(
            anchorDate = anchorDate,
            payPeriodCadence = cadence,
            payPeriodPreview = preview?.let {
                "${it.startInclusive} – ${it.endExclusive.minusDays(1)}"
            } ?: "Enter a valid start date to preview this period.",
            workdays = DayOfWeek.entries.map { day ->
                WorkdayUiModel(day.name, day in schedule.trackableDays)
            },
            minimalActiveNotificationEnabled = appSettings.minimalActiveNotificationEnabled,
            liveTimerNotificationEnabled = appSettings.liveTimerNotificationEnabled,
            privacyDisclosureAccepted = appSettings.privacyDisclosureAccepted,
            hubActivationDate = sync.settings.activationDate?.toString().orEmpty(),
            hubOutboxConfigured = sync.settings.outboxTreeUri != null,
            hubProviderVerified = sync.settings.documentProviderVerified,
            hubTransport = sync.settings.transportKind,
            hubSyncStatus = "${sync.status.pending} pending · ${sync.status.failed} failed · " +
                "${sync.status.accepted} accepted — ${sync.status.lastMessage}",
            statusMessage = sync.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updateAnchorDate(value: String) {
        editedAnchorDate.value = value
    }

    fun selectPayPeriodCadence(cadence: PayPeriodCadence) {
        editedCadence.value = cadence
    }

    fun saveAnchorDate() {
        viewModelScope.launch {
            val date = runCatching {
                LocalDate.parse(editedAnchorDate.value ?: uiState.value.anchorDate)
            }.getOrElse { error ->
                statusMessage.value = "Use YYYY-MM-DD for the period start."
                logger.warn(LogCategory.SETTINGS, "Pay period save rejected", error = error)
                return@launch
            }
            val cadence = editedCadence.value ?: uiState.value.payPeriodCadence
            runCatching {
                payPeriodSettingsRepository.setSettings(PayPeriodSettings(date, cadence))
            }.onSuccess {
                editedAnchorDate.value = null
                editedCadence.value = null
                statusMessage.value = "${cadence.label} pay period saved."
                logger.info(
                    LogCategory.SETTINGS,
                    "Pay period saved",
                    mapOf("anchorDate" to date.toString(), "cadence" to cadence.name),
                )
            }.onFailure { error ->
                statusMessage.value = "Pay period could not be saved."
                logger.warn(LogCategory.SETTINGS, "Pay period save rejected", error = error)
            }
        }
    }

    fun setDayTrackable(dayName: String, trackable: Boolean) {
        viewModelScope.launch {
            runCatching {
                val day = DayOfWeek.valueOf(dayName)
                val current = workScheduleRepository.getWorkSchedule()
                val updatedDays = if (trackable) {
                    current.trackableDays + day
                } else {
                    current.trackableDays - day
                }
                workScheduleRepository.setWorkSchedule(WorkSchedule(updatedDays))
                day
            }.onSuccess { day ->
                statusMessage.value = if (uiState.value.workdays.count { it.trackable } == 0 && !trackable) {
                    "Automatic tracking paused because no days are selected."
                } else {
                    "${day.name.lowercase().replaceFirstChar(Char::uppercase)} tracking updated."
                }
                logger.info(
                    LogCategory.SETTINGS,
                    "Workday setting changed",
                    mapOf("day" to day.name, "trackable" to trackable),
                )
            }.onFailure { error ->
                statusMessage.value = "Workday change could not be saved."
                logger.warn(LogCategory.SETTINGS, "Workday setting change failed", error = error)
            }
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
                appSettingsRepository.setActivityDetectionEnabled(true)
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
                appSettingsRepository.setActivityDetectionEnabled(false)
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

    fun setHubActivationDate(value: String) {
        runCatching { value.trim().ifBlank { null }?.let(LocalDate::parse) }
            .onSuccess { workHubSettingsGateway.setActivationDate(it) }
            .onFailure { statusMessage.value = "Use YYYY-MM-DD for the Work Hub activation date." }
    }

    fun setHubOutboxTree(uri: Uri) {
        runCatching { workHubSettingsGateway.setOutboxTree(uri) }
            .onSuccess { statusMessage.value = "Outbox folder selected. Run the provider test before enabling sync." }
            .onFailure { statusMessage.value = it.message ?: "Could not retain access to that folder." }
    }

    fun verifyHubProvider() {
        viewModelScope.launch {
            val passed = runCatching { workHubSettingsGateway.verifyProvider() }.getOrDefault(false)
            statusMessage.value = if (passed) {
                "Outbox provider passed create, rename, and read checks."
            } else {
                "Provider test failed. Direct Notion fallback is available."
            }
        }
    }

    fun setHubTransport(kind: HubTransportKind) = workHubSettingsGateway.setTransport(kind)

    fun saveNotionFallback(token: String, dailyLogId: String, projectsId: String, timeRecordsId: String) {
        runCatching {
            workHubSettingsGateway.saveNotionFallback(token, dailyLogId, projectsId, timeRecordsId)
        }.onSuccess { statusMessage.value = "Direct Notion fallback credentials saved securely on this device." }
            .onFailure { statusMessage.value = it.message ?: "Could not save the fallback connection." }
    }

    fun syncHubNow() {
        viewModelScope.launch {
            runCatching { workHubSettingsGateway.syncNow() }
                .onSuccess {
                    statusMessage.value = if (it == null) {
                        "No eligible Work Hub records are pending."
                    } else {
                        "Work Hub transfer ${it.transferId.take(8)} created."
                    }
                }
                .onFailure { statusMessage.value = it.message ?: "Work Hub sync failed." }
        }
    }

    fun fullResyncHub() {
        viewModelScope.launch {
            runCatching { workHubSettingsGateway.fullResync() }
                .onSuccess { statusMessage.value = "Full Work Hub resync queued from the activation date." }
                .onFailure { statusMessage.value = it.message ?: "Full Work Hub resync failed." }
        }
    }

    fun exportEncryptedBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            runCatching { workHubSettingsGateway.exportBackup(uri, passphrase.toCharArray()) }
                .onSuccess { statusMessage.value = "Encrypted Time Tracker backup created." }
                .onFailure { statusMessage.value = it.message ?: "Backup export failed." }
        }
    }

    fun importEncryptedBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            runCatching { workHubSettingsGateway.importBackup(uri, passphrase.toCharArray()) }
                .onSuccess { statusMessage.value = "Encrypted backup restored and work geofences registered." }
                .onFailure { statusMessage.value = it.message ?: "Backup import failed. Check the passphrase and destination state." }
        }
    }
}

data class SettingsUiState(
    val anchorDate: String = LocalDate.now().toString(),
    val payPeriodCadence: PayPeriodCadence = PayPeriodCadence.BIWEEKLY,
    val payPeriodPreview: String = "",
    val workdays: List<WorkdayUiModel> = DayOfWeek.entries.map { WorkdayUiModel(it.name, it in WorkSchedule.DEFAULT_TRACKABLE_DAYS) },
    val minimalActiveNotificationEnabled: Boolean = false,
    val liveTimerNotificationEnabled: Boolean = false,
    val privacyDisclosureAccepted: Boolean = false,
    val hubActivationDate: String = "",
    val hubOutboxConfigured: Boolean = false,
    val hubProviderVerified: Boolean = false,
    val hubTransport: HubTransportKind = HubTransportKind.DOCUMENT_TREE,
    val hubSyncStatus: String = "Not configured",
    val statusMessage: String = "",
)

data class WorkdayUiModel(val name: String, val trackable: Boolean)

private data class PayPeriodEdits(val anchorDate: String?, val cadence: PayPeriodCadence?)
private data class SyncState(val message: String, val settings: HubSyncSettings, val status: HubSyncStatus)
