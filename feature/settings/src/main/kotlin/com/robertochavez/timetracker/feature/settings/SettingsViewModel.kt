package com.robertochavez.timetracker.feature.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.robertochavez.timetracker.core.sync.HubProviderGateState
import com.robertochavez.timetracker.core.sync.HubSyncSettings
import com.robertochavez.timetracker.core.sync.HubSyncStatus
import com.robertochavez.timetracker.core.sync.HubTransportKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    var legacyBackupPassphrase by mutableStateOf("")
    var notionToken by mutableStateOf("")
    private val editedAnchorDate = MutableStateFlow<String?>(null)
    private val editedCadence = MutableStateFlow<PayPeriodCadence?>(null)
    private val statusMessage = MutableStateFlow("")
    private val runningAction = MutableStateFlow<String?>(null)
    private val scheduleMutex = Mutex()
    private val payPeriodEdits = combine(editedAnchorDate, editedCadence, ::PayPeriodEdits)
    private val syncState = combine(
        statusMessage,
        workHubSettingsGateway.settings,
        workHubSettingsGateway.status,
        runningAction,
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
                "${it.startInclusive.format(DateTimeFormatter.ofPattern("MMM d"))} – " +
                    it.endExclusive.minusDays(1).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
            } ?: "Enter a valid start date to preview this period.",
            workdays = DayOfWeek.entries.map { day ->
                WorkdayUiModel(day.name, day in schedule.trackableDays)
            },
            minimalActiveNotificationEnabled = appSettings.minimalActiveNotificationEnabled,
            liveTimerNotificationEnabled = appSettings.liveTimerNotificationEnabled,
            privacyDisclosureAccepted = appSettings.privacyDisclosureAccepted,
            activityDetectionEnabled = appSettings.activityDetectionEnabled,
            payPeriodEdited = anchorDate != payPeriod.anchorStartDate.toString() || cadence != payPeriod.cadence,
            runningAction = sync.action,
            hubActivationDate = sync.settings.activationDate?.toString().orEmpty(),
            hubOutboxConfigured = sync.settings.outboxTreeUri != null,
            hubProviderGateState = sync.settings.providerGateState,
            hubTransport = sync.settings.transportKind,
            hubSyncStatus = "${sync.status.pending} pending · ${sync.status.failed} failed · " +
                "${sync.status.accepted} accepted — ${sync.status.lastMessage}",
            hubDailyLogId = sync.settings.dailyLogDatabaseId,
            hubProjectsId = sync.settings.projectsDatabaseId,
            hubTimeRecordsId = sync.settings.timeRecordsDatabaseId,
            hubFailedCount = sync.status.failed,
            hubFallbackConfigured = workHubSettingsGateway.hasNotionToken() &&
                listOf(sync.settings.dailyLogDatabaseId, sync.settings.projectsDatabaseId, sync.settings.timeRecordsDatabaseId)
                    .all { it.isNotBlank() },
            statusMessage = sync.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private fun launchAction(name: String, action: suspend () -> Unit) {
        if (runningAction.value != null) return
        runningAction.value = name
        viewModelScope.launch {
            try {
                action()
            } finally {
                runningAction.value = null
            }
        }
    }

    fun clearStatus() {
        statusMessage.value = ""
    }

    fun discardPayPeriodEdits() {
        editedAnchorDate.value = null
        editedCadence.value = null
    }

    fun updateAnchorDate(value: String) {
        editedAnchorDate.value = value
    }

    fun selectPayPeriodCadence(cadence: PayPeriodCadence) {
        editedCadence.value = cadence
    }

    fun saveAnchorDate() {
        if (runningAction.value != null) return
        runningAction.value = "pay_period"
        viewModelScope.launch {
            val date = runCatching {
                LocalDate.parse(editedAnchorDate.value ?: uiState.value.anchorDate)
            }.getOrElse { error ->
                statusMessage.value = "Use YYYY-MM-DD for the period start."
                logger.warn(LogCategory.SETTINGS, "Pay period save rejected", error = error)
                runningAction.value = null
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
            runningAction.value = null
        }
    }

    fun setDayTrackable(dayName: String, trackable: Boolean) {
        viewModelScope.launch {
            runCatching {
                scheduleMutex.withLock {
                    val day = DayOfWeek.valueOf(dayName)
                    val current = workScheduleRepository.getWorkSchedule()
                    val updatedDays = if (trackable) current.trackableDays + day else current.trackableDays - day
                    workScheduleRepository.setWorkSchedule(WorkSchedule(updatedDays))
                    day
                }
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
        if (runningAction.value != null) return
        runningAction.value = "activity"
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
            runningAction.value = null
        }
    }

    fun unregisterActivityTransitions() {
        if (runningAction.value != null) return
        runningAction.value = "activity"
        viewModelScope.launch {
            runCatching {
                activityTransitionRegistrar.unregisterDriveAndIdleTransitions()
            }.onSuccess {
                appSettingsRepository.setActivityDetectionEnabled(false)
                statusMessage.value = "Activity detection disabled."
                logger.info(LogCategory.ACTIVITY, "Activity detection disabled from settings")
            }.onFailure {
                statusMessage.value = "Activity detection could not be disabled."
            }
            runningAction.value = null
        }
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
            .onSuccess {
                workHubSettingsGateway.setActivationDate(it)
                statusMessage.value = if (it == null) "Exports paused. Local tracking is unchanged." else "First sync date saved."
            }
            .onFailure { statusMessage.value = "Use YYYY-MM-DD for the Work Hub activation date." }
    }

    fun setHubOutboxTree(uri: Uri) {
        runCatching { workHubSettingsGateway.setOutboxTree(uri) }
            .onSuccess { statusMessage.value = "Outbox folder selected. Run the provider test before enabling sync." }
            .onFailure { statusMessage.value = it.message ?: "Could not retain access to that folder." }
    }

    fun verifyHubProvider() {
        launchAction("provider") {
            val passed = runCatching { withContext(Dispatchers.IO) { workHubSettingsGateway.verifyProvider() } }.getOrDefault(false)
            statusMessage.value = if (passed) {
                "Basic file checks passed. Complete the restart, reboot, background receipt, and laptop-sync checks on the work phone."
            } else {
                "Basic provider check failed. Direct Notion fallback is now available."
            }
        }
    }

    fun recordFullHubProviderGate(passed: Boolean) {
        runCatching { workHubSettingsGateway.recordFullProviderGate(passed) }
            .onSuccess {
                statusMessage.value = if (passed) {
                    "Full work-phone OneDrive gate recorded as passed."
                } else {
                    "Full work-phone OneDrive gate recorded as failed. Direct Notion fallback is available."
                }
            }
            .onFailure { statusMessage.value = it.message ?: "Could not record the provider gate result." }
    }

    fun setHubTransport(kind: HubTransportKind) {
        runCatching { workHubSettingsGateway.setTransport(kind) }
            .onFailure { statusMessage.value = it.message ?: "That Work Hub transport is not available yet." }
    }

    fun saveNotionFallback(token: String, dailyLogId: String, projectsId: String, timeRecordsId: String) {
        runCatching {
            require(uiState.value.hubFallbackAllowed) { "Complete the OneDrive check before using the fallback." }
            workHubSettingsGateway.saveNotionFallback(token, dailyLogId, projectsId, timeRecordsId)
            workHubSettingsGateway.setTransport(HubTransportKind.DIRECT_NOTION)
            notionToken = ""
        }.onSuccess { statusMessage.value = "Direct Notion fallback credentials saved securely on this device." }
            .onFailure { statusMessage.value = it.message ?: "Could not save the fallback connection." }
    }

    fun syncHubNow() {
        launchAction("sync") {
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
        launchAction("sync") {
            runCatching { workHubSettingsGateway.fullResync() }
                .onSuccess { statusMessage.value = "Full Work Hub resync queued from the activation date." }
                .onFailure { statusMessage.value = it.message ?: "Full Work Hub resync failed." }
        }
    }

    fun discardFailedHubRecords() {
        launchAction("sync") {
            runCatching { workHubSettingsGateway.discardFailed() }
                .onSuccess { statusMessage.value = "Failed Work Hub records discarded. Local tracking data was not changed." }
                .onFailure { statusMessage.value = it.message ?: "Failed Work Hub records could not be discarded." }
        }
    }

    fun exportBackup(uri: Uri) {
        launchAction("backup") {
            runCatching { workHubSettingsGateway.exportBackup(uri) }
                .onSuccess { statusMessage.value = "Time Tracker JSON backup created." }
                .onFailure { statusMessage.value = it.message ?: "Backup export failed." }
        }
    }

    fun importBackup(uri: Uri, passphrase: String) {
        launchAction("backup") {
            runCatching { workHubSettingsGateway.importBackup(uri, passphrase.toCharArray()) }
                .onSuccess {
                    legacyBackupPassphrase = ""
                    statusMessage.value = "Backup restored and work geofences registered."
                }
                .onFailure { statusMessage.value = it.message ?: "Backup import failed. Check the file and destination state." }
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
    val activityDetectionEnabled: Boolean = false,
    val payPeriodEdited: Boolean = false,
    val runningAction: String? = null,
    val hubActivationDate: String = "",
    val hubOutboxConfigured: Boolean = false,
    val hubProviderGateState: HubProviderGateState = HubProviderGateState.NOT_RUN,
    val hubTransport: HubTransportKind = HubTransportKind.DOCUMENT_TREE,
    val hubSyncStatus: String = "Not configured",
    val hubDailyLogId: String = "",
    val hubProjectsId: String = "",
    val hubTimeRecordsId: String = "",
    val hubFailedCount: Int = 0,
    val hubFallbackConfigured: Boolean = false,
    val statusMessage: String = "",
)

data class WorkdayUiModel(val name: String, val trackable: Boolean)

private data class PayPeriodEdits(val anchorDate: String?, val cadence: PayPeriodCadence?)
private data class SyncState(val message: String, val settings: HubSyncSettings, val status: HubSyncStatus, val action: String?)
