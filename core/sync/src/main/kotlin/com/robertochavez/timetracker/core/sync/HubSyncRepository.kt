package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.database.dao.HubSyncRecordDao
import com.robertochavez.timetracker.core.database.entity.HubSyncRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HubSyncRepository @Inject constructor(private val dao: HubSyncRecordDao, private val clock: Clock) {
    fun observeStatus(): Flow<HubSyncStatus> = dao.observeAll().map { records ->
        HubSyncStatus(
            pending = records.count { it.state == HubSyncState.PENDING.name || it.state == HubSyncState.TRANSFERRED.name },
            failed = records.count { it.state == HubSyncState.FAILED.name },
            accepted = records.count { it.state == HubSyncState.ACCEPTED.name },
            lastMessage = records.firstOrNull()?.let { "${it.state.lowercase()} · ${it.sourceId}" } ?: "No records queued",
        )
    }

    suspend fun enqueue(sourceId: String, type: HubPayloadType, json: String) {
        val now = clock.millis()
        val hash = sha256(json)
        val existing = dao.get(sourceId)
        if (existing?.contentHash == hash) return
        dao.upsert(
            HubSyncRecordEntity(
                sourceId = sourceId,
                payloadType = type.name,
                deterministicJson = json,
                contentHash = hash,
                state = HubSyncState.PENDING.name,
                transferId = null,
                attemptCount = 0,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
                lastAttemptAtEpochMillis = null,
                sanitizedError = null,
            ),
        )
    }

    suspend fun transferable(): List<HubSyncRecordEntity> = dao.getTransferable()

    suspend fun requeueStaleTransfers() = dao.requeueStaleTransfers(clock.millis() - STALE_TRANSFER_MILLIS)

    suspend fun transferred(records: List<HubSyncRecordEntity>, transferId: String) =
        update(records, HubSyncState.TRANSFERRED, transferId, null)

    suspend fun applyReceipt(receipt: HubReceipt) {
        receipt.acceptedSourceIds.forEach { sourceId ->
            dao.get(sourceId)?.let { update(listOf(it), HubSyncState.ACCEPTED, receipt.transferId, null) }
        }
        receipt.rejected.forEach { (sourceId, reason) ->
            dao.get(sourceId)?.let { update(listOf(it), HubSyncState.FAILED, receipt.transferId, reason) }
        }
    }

    suspend fun failed(records: List<HubSyncRecordEntity>, error: String) = update(records, HubSyncState.FAILED, null, sanitize(error))

    suspend fun transientFailure(records: List<HubSyncRecordEntity>, error: String) {
        val now = clock.millis()
        val sanitized = sanitize(error)
        records.forEach { record ->
            val attemptCount = record.attemptCount + 1
            dao.upsert(
                record.copy(
                    state = if (attemptCount >= MAX_TRANSFER_ATTEMPTS) HubSyncState.FAILED.name else HubSyncState.PENDING.name,
                    transferId = null,
                    attemptCount = attemptCount,
                    updatedAtEpochMillis = now,
                    lastAttemptAtEpochMillis = now,
                    sanitizedError = sanitized,
                ),
            )
        }
    }

    suspend fun fullResync() {
        dao.markAllPending()
    }

    suspend fun discardFailed() = dao.deleteFailed()

    private suspend fun update(records: List<HubSyncRecordEntity>, state: HubSyncState, transferId: String?, error: String?) {
        val now = clock.millis()
        records.forEach { record ->
            dao.upsert(
                record.copy(
                    state = state.name,
                    transferId = transferId ?: record.transferId,
                    attemptCount = record.attemptCount + if (state == HubSyncState.TRANSFERRED || state == HubSyncState.FAILED) 1 else 0,
                    updatedAtEpochMillis = now,
                    lastAttemptAtEpochMillis = now,
                    sanitizedError = error,
                ),
            )
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun sanitize(value: String): String = value.replace(Regex("[\\r\\n]+"), " ").take(240)

    private companion object {
        const val STALE_TRANSFER_MILLIS = 24L * 60L * 60L * 1_000L
        const val MAX_TRANSFER_ATTEMPTS = 6
    }
}
