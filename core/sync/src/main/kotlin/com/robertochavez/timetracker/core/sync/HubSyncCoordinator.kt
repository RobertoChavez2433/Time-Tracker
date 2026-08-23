package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.database.entity.HubSyncRecordEntity
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HubSyncCoordinator @Inject constructor(
    private val repository: HubSyncRepository,
    private val settingsStore: HubSyncSettingsStore,
    private val documentTreeTransport: DocumentTreeHubSyncTransport,
    private val directNotionTransport: DirectNotionHubSyncTransport,
    private val summaryEnqueuer: HubSummaryEnqueuer,
) {
    fun observeStatus(): Flow<HubSyncStatus> = repository.observeStatus()

    suspend fun syncNow(): HubTransferResult? {
        summaryEnqueuer.enqueueEligible()
        repository.requeueStaleTransfers()
        val records = repository.transferable().filterByActivationDate(settingsStore.current())
        if (records.isEmpty()) return null
        val transport = selectedTransport()
        return runCatching { transport.transfer(records) }
            .onSuccess { result ->
                repository.transferred(records, result.transferId)
                if (result.acceptedSourceIds.isNotEmpty() || result.rejected.isNotEmpty()) {
                    repository.applyReceipt(
                        HubReceipt(result.transferId, result.acceptedSourceIds, result.rejected, java.time.Instant.now()),
                    )
                }
            }
            .onFailure { error ->
                if (error is IOException) {
                    repository.transientFailure(records, error.message ?: "Sync failed")
                } else {
                    repository.failed(records, error.message ?: "Sync failed")
                }
            }
            .getOrThrow()
    }

    suspend fun ingestReceipts(): Int {
        val receipts = documentTreeTransport.readReceipts()
        receipts.forEach { repository.applyReceipt(it) }
        return receipts.size
    }

    fun verifyDocumentProvider(): Boolean = documentTreeTransport.runBasicProviderCheck().also(settingsStore::recordBasicProviderCheck)

    fun recordFullProviderGate(passed: Boolean) = settingsStore.recordFullProviderGate(passed)

    suspend fun fullResync(): HubTransferResult? {
        repository.fullResync()
        return syncNow()
    }

    suspend fun discardFailed() = repository.discardFailed()

    private fun selectedTransport(): HubSyncTransport {
        val settings = settingsStore.current()
        return when (settings.transportKind) {
            HubTransportKind.DOCUMENT_TREE -> {
                require(settings.providerGateState == HubProviderGateState.FULL_PASSED) {
                    "Complete the full work-phone OneDrive gate before enabling outbox sync."
                }
                documentTreeTransport
            }
            HubTransportKind.DIRECT_NOTION -> {
                require(
                    settings.providerGateState == HubProviderGateState.BASIC_FAILED ||
                        settings.providerGateState == HubProviderGateState.FULL_FAILED,
                ) { "Direct Notion is available only after the OneDrive provider gate fails." }
                directNotionTransport
            }
        }
    }

    private fun List<HubSyncRecordEntity>.filterByActivationDate(settings: HubSyncSettings): List<HubSyncRecordEntity> {
        val cutoff = settings.activationDate ?: return emptyList()
        return filter { record ->
            val date = Regex("\\d{4}-\\d{2}-\\d{2}").find(record.sourceId)?.value
            date != null && !java.time.LocalDate.parse(date).isBefore(cutoff)
        }
    }
}
