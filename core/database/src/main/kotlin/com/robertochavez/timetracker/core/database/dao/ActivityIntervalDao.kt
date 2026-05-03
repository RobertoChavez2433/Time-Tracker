package com.robertochavez.timetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.robertochavez.timetracker.core.database.entity.ActivityIntervalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityIntervalDao {
    @Query("SELECT * FROM activity_intervals ORDER BY startEpochMillis ASC")
    fun observeIntervals(): Flow<List<ActivityIntervalEntity>>

    @Query("SELECT * FROM activity_intervals WHERE sessionId = :sessionId ORDER BY startEpochMillis ASC")
    fun observeIntervalsForSession(sessionId: String): Flow<List<ActivityIntervalEntity>>

    @Query(
        "SELECT * FROM activity_intervals WHERE sessionId = :sessionId " +
            "AND endEpochMillis IS NULL ORDER BY startEpochMillis DESC LIMIT 1",
    )
    suspend fun getOpenInterval(sessionId: String): ActivityIntervalEntity?

    @Upsert
    suspend fun upsert(entity: ActivityIntervalEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ActivityIntervalEntity>)

    @Query("DELETE FROM activity_intervals WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM activity_intervals WHERE id = :id")
    suspend fun deleteById(id: String)
}
