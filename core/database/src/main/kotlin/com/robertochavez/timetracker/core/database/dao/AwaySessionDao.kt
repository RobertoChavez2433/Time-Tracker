package com.robertochavez.timetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.robertochavez.timetracker.core.database.entity.AwaySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AwaySessionDao {
    @Query("SELECT * FROM away_sessions ORDER BY startEpochMillis DESC")
    fun observeSessions(): Flow<List<AwaySessionEntity>>

    @Query("SELECT * FROM away_sessions WHERE endEpochMillis IS NULL ORDER BY startEpochMillis DESC LIMIT 1")
    suspend fun getActiveSession(): AwaySessionEntity?

    @Query("SELECT * FROM away_sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): AwaySessionEntity?

    @Upsert
    suspend fun upsert(entity: AwaySessionEntity)
}
