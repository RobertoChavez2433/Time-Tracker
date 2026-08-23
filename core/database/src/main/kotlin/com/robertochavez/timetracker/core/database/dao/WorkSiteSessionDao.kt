package com.robertochavez.timetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.robertochavez.timetracker.core.database.entity.WorkSiteSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkSiteSessionDao {
    @Query("SELECT * FROM work_site_sessions ORDER BY startEpochMillis ASC")
    fun observeWorkSiteSessions(): Flow<List<WorkSiteSessionEntity>>

    @Query(
        "SELECT * FROM work_site_sessions WHERE startEpochMillis < :endExclusiveEpochMillis " +
            "AND (endEpochMillis IS NULL OR endEpochMillis > :startInclusiveEpochMillis) " +
            "ORDER BY startEpochMillis ASC",
    )
    fun observeWorkSiteSessionsInRange(startInclusiveEpochMillis: Long, endExclusiveEpochMillis: Long): Flow<List<WorkSiteSessionEntity>>

    @Query(
        "SELECT * FROM work_site_sessions WHERE workLocationId = :workLocationId " +
            "AND endEpochMillis IS NULL ORDER BY startEpochMillis DESC LIMIT 1",
    )
    suspend fun getActiveForLocation(workLocationId: String): WorkSiteSessionEntity?

    @Query("SELECT * FROM work_site_sessions WHERE endEpochMillis IS NULL ORDER BY startEpochMillis DESC LIMIT 1")
    suspend fun getActive(): WorkSiteSessionEntity?

    @Query("SELECT * FROM work_site_sessions ORDER BY startEpochMillis ASC")
    suspend fun getWorkSiteSessions(): List<WorkSiteSessionEntity>

    @Query(
        "UPDATE work_site_sessions SET projectNumberSnapshot = :projectNumber " +
            "WHERE workLocationId = :workLocationId AND projectNumberSnapshot IS NULL",
    )
    suspend fun backfillNullProjectNumberSnapshots(workLocationId: String, projectNumber: String)

    @Upsert
    suspend fun upsert(entity: WorkSiteSessionEntity)

    @Query("DELETE FROM work_site_sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}
