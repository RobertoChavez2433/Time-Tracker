package com.robertochavez.timetracker.feature.settings

import android.net.Uri
import com.robertochavez.timetracker.core.sync.HubBackupService
import com.robertochavez.timetracker.core.sync.HubSyncCoordinator
import com.robertochavez.timetracker.core.sync.HubSyncSettings
import com.robertochavez.timetracker.core.sync.HubSyncSettingsStore
import com.robertochavez.timetracker.core.sync.HubSyncStatus
import com.robertochavez.timetracker.core.sync.HubTransferResult
import com.robertochavez.timetracker.core.sync.HubTransportKind
import com.robertochavez.timetracker.core.sync.NotionTokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import javax.inject.Inject

@Suppress("ComplexInterface", "TooManyFunctions")
interface WorkHubSettingsGateway {
    val settings: Flow<HubSyncSettings>
    val status: Flow<HubSyncStatus>
    fun hasNotionToken(): Boolean = false
    fun setActivationDate(value: LocalDate?)
    fun setOutboxTree(uri: Uri)
    fun verifyProvider(): Boolean
    fun recordFullProviderGate(passed: Boolean)
    fun setTransport(kind: HubTransportKind)
    fun saveNotionFallback(token: String, dailyLogId: String, projectsId: String, timeRecordsId: String)
    suspend fun syncNow(): HubTransferResult?
    suspend fun fullResync(): HubTransferResult?
    suspend fun discardFailed()
    suspend fun exportBackup(uri: Uri)
    suspend fun importBackup(uri: Uri, passphrase: CharArray)
}

class RealWorkHubSettingsGateway @Inject constructor(
    private val coordinator: HubSyncCoordinator,
    private val settingsStore: HubSyncSettingsStore,
    private val tokenStore: NotionTokenStore,
    private val backupService: HubBackupService,
) : WorkHubSettingsGateway {
    override val settings = settingsStore.settings
    override val status = coordinator.observeStatus()
    override fun hasNotionToken(): Boolean = runCatching { !tokenStore.read().isNullOrBlank() }.getOrDefault(false)
    override fun setActivationDate(value: LocalDate?) = settingsStore.setActivationDate(value)
    override fun setOutboxTree(uri: Uri) = settingsStore.setOutboxTree(uri)
    override fun verifyProvider() = coordinator.verifyDocumentProvider()
    override fun recordFullProviderGate(passed: Boolean) = coordinator.recordFullProviderGate(passed)
    override fun setTransport(kind: HubTransportKind) = settingsStore.setTransport(kind)
    override fun saveNotionFallback(token: String, dailyLogId: String, projectsId: String, timeRecordsId: String) {
        tokenStore.save(token)
        settingsStore.setNotionDatabaseIds(dailyLogId, projectsId, timeRecordsId)
    }
    override suspend fun syncNow() = coordinator.syncNow()
    override suspend fun fullResync() = coordinator.fullResync()
    override suspend fun discardFailed() = coordinator.discardFailed()
    override suspend fun exportBackup(uri: Uri) = backupService.export(uri)
    override suspend fun importBackup(uri: Uri, passphrase: CharArray) = backupService.import(uri, passphrase)
}

object NoopWorkHubSettingsGateway : WorkHubSettingsGateway {
    override val settings = flowOf(HubSyncSettings())
    override val status = flowOf(HubSyncStatus())
    override fun setActivationDate(value: LocalDate?) = Unit
    override fun setOutboxTree(uri: Uri) = Unit
    override fun verifyProvider() = false
    override fun recordFullProviderGate(passed: Boolean) = Unit
    override fun setTransport(kind: HubTransportKind) = Unit
    override fun saveNotionFallback(token: String, dailyLogId: String, projectsId: String, timeRecordsId: String) = Unit
    override suspend fun syncNow(): HubTransferResult? = null
    override suspend fun fullResync(): HubTransferResult? = null
    override suspend fun discardFailed() = Unit
    override suspend fun exportBackup(uri: Uri) = Unit
    override suspend fun importBackup(uri: Uri, passphrase: CharArray) = Unit
}
