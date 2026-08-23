package com.robertochavez.timetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.robertochavez.timetracker.core.database.entity.HubSyncRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HubSyncRecordDao {
    @Query("SELECT * FROM hub_sync_records ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<HubSyncRecordEntity>>

    @Query("SELECT * FROM hub_sync_records WHERE state = 'PENDING' ORDER BY updatedAtEpochMillis ASC")
    suspend fun getTransferable(): List<HubSyncRecordEntity>

    @Query("UPDATE hub_sync_records SET state = 'PENDING', transferId = NULL WHERE state = 'TRANSFERRED' AND updatedAtEpochMillis < :cutoffEpochMillis")
    suspend fun requeueStaleTransfers(cutoffEpochMillis: Long)

    @Query("SELECT * FROM hub_sync_records WHERE sourceId = :sourceId LIMIT 1")
    suspend fun get(sourceId: String): HubSyncRecordEntity?

    @Upsert
    suspend fun upsert(record: HubSyncRecordEntity)

    @Query("DELETE FROM hub_sync_records")
    suspend fun deleteAll()

    @Query("UPDATE hub_sync_records SET state = 'PENDING', sanitizedError = NULL")
    suspend fun markAllPending()

    @Query("DELETE FROM hub_sync_records WHERE state = 'FAILED'")
    suspend fun deleteFailed()
}
