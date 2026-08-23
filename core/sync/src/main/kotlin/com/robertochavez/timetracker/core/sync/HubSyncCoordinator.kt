package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.database.entity.HubSyncRecordEntity
import kotlinx.coroutines.flow.Flow
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
            .onFailure { repository.failed(records, it.message ?: "Sync failed") }
            .getOrThrow()
    }

    suspend fun ingestReceipts(): Int {
        val receipts = documentTreeTransport.readReceipts()
        receipts.forEach { repository.applyReceipt(it) }
        return receipts.size
    }

    fun verifyDocumentProvider(): Boolean = documentTreeTransport.verifyProviderGate()

    suspend fun fullResync(): HubTransferResult? {
        repository.fullResync()
        return syncNow()
    }

    suspend fun discardFailed() = repository.discardFailed()

    private fun selectedTransport(): HubSyncTransport = when (settingsStore.current().transportKind) {
        HubTransportKind.DOCUMENT_TREE -> documentTreeTransport
        HubTransportKind.DIRECT_NOTION -> directNotionTransport
    }

    private fun List<HubSyncRecordEntity>.filterByActivationDate(settings: HubSyncSettings): List<HubSyncRecordEntity> {
        val cutoff = settings.activationDate ?: return emptyList()
        return filter { record ->
            val date = Regex("\\d{4}-\\d{2}-\\d{2}").find(record.sourceId)?.value
            date != null && !java.time.LocalDate.parse(date).isBefore(cutoff)
        }
    }
}
