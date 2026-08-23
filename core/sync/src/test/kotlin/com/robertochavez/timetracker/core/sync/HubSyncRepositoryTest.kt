package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.database.dao.HubSyncRecordDao
import com.robertochavez.timetracker.core.database.entity.HubSyncRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HubSyncRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC)

    @Test fun `unchanged failed payload remains failed until explicit action`() = runBlocking {
        val dao = FakeHubSyncRecordDao()
        val repository = HubSyncRepository(dao, clock)
        repository.enqueue("day:v1:2026-08-23", HubPayloadType.DAILY_SUMMARY, "{\"value\":1}")
        dao.upsert(dao.get("day:v1:2026-08-23")!!.copy(state = HubSyncState.FAILED.name))

        repository.enqueue("day:v1:2026-08-23", HubPayloadType.DAILY_SUMMARY, "{\"value\":1}")

        assertEquals(HubSyncState.FAILED.name, dao.get("day:v1:2026-08-23")!!.state)
        assertEquals(emptyList<HubSyncRecordEntity>(), repository.transferable())
    }

    @Test fun `corrected failed payload becomes pending`() = runBlocking {
        val dao = FakeHubSyncRecordDao()
        val repository = HubSyncRepository(dao, clock)
        repository.enqueue("day:v1:2026-08-23", HubPayloadType.DAILY_SUMMARY, "{\"value\":1}")
        dao.upsert(dao.get("day:v1:2026-08-23")!!.copy(state = HubSyncState.FAILED.name))

        repository.enqueue("day:v1:2026-08-23", HubPayloadType.DAILY_SUMMARY, "{\"value\":2}")

        assertEquals(HubSyncState.PENDING.name, dao.get("day:v1:2026-08-23")!!.state)
    }

    @Test fun `stale transfers requeue and failed records can be discarded`() = runBlocking {
        val dao = FakeHubSyncRecordDao()
        val repository = HubSyncRepository(dao, clock)
        repository.enqueue("day:v1:2026-08-22", HubPayloadType.DAILY_SUMMARY, "{}")
        val record = dao.get("day:v1:2026-08-22")!!
        dao.upsert(record.copy(state = HubSyncState.TRANSFERRED.name, updatedAtEpochMillis = clock.millis() - 25L * 60L * 60L * 1_000L))
        repository.enqueue("project-day:v1:site:2026-08-22", HubPayloadType.PROJECT_DAY, "{}")
        dao.upsert(dao.get("project-day:v1:site:2026-08-22")!!.copy(state = HubSyncState.FAILED.name))

        repository.requeueStaleTransfers()
        repository.discardFailed()

        assertEquals(HubSyncState.PENDING.name, dao.get("day:v1:2026-08-22")!!.state)
        assertNull(dao.get("project-day:v1:site:2026-08-22"))
    }

    @Test fun `transient failures remain transferable until bounded attempt limit`() = runBlocking {
        val dao = FakeHubSyncRecordDao()
        val repository = HubSyncRepository(dao, clock)
        val sourceId = "day:v1:2026-08-23"
        repository.enqueue(sourceId, HubPayloadType.DAILY_SUMMARY, "{}")

        repeat(5) {
            repository.transientFailure(listOf(dao.get(sourceId)!!), "temporary provider error")
            assertEquals(HubSyncState.PENDING.name, dao.get(sourceId)!!.state)
            assertEquals(1, repository.transferable().size)
        }
        repository.transientFailure(listOf(dao.get(sourceId)!!), "temporary provider error")

        assertEquals(HubSyncState.FAILED.name, dao.get(sourceId)!!.state)
        assertEquals(emptyList<HubSyncRecordEntity>(), repository.transferable())
    }
}

private class FakeHubSyncRecordDao : HubSyncRecordDao {
    private val records = linkedMapOf<String, HubSyncRecordEntity>()
    private val flow = MutableStateFlow<List<HubSyncRecordEntity>>(emptyList())

    override fun observeAll(): Flow<List<HubSyncRecordEntity>> = flow
    override suspend fun getTransferable() = records.values.filter { it.state == HubSyncState.PENDING.name }
    override suspend fun requeueStaleTransfers(cutoffEpochMillis: Long) {
        records.replaceAll { _, value ->
            if (value.state == HubSyncState.TRANSFERRED.name && value.updatedAtEpochMillis < cutoffEpochMillis) {
                value.copy(state = HubSyncState.PENDING.name, transferId = null)
            } else {
                value
            }
        }
        publish()
    }
    override suspend fun get(sourceId: String) = records[sourceId]
    override suspend fun upsert(record: HubSyncRecordEntity) {
        records[record.sourceId] = record
        publish()
    }
    override suspend fun deleteAll() {
        records.clear()
        publish()
    }
    override suspend fun markAllPending() {
        records.replaceAll { _, value -> value.copy(state = HubSyncState.PENDING.name, sanitizedError = null) }
        publish()
    }
    override suspend fun deleteFailed() {
        records.values.removeAll { it.state == HubSyncState.FAILED.name }
        publish()
    }
    private fun publish() {
        flow.value = records.values.toList()
    }
}
