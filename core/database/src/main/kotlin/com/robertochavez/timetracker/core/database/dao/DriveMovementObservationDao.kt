package com.robertochavez.timetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.robertochavez.timetracker.core.database.entity.DriveMovementObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveMovementObservationDao {
    @Query("SELECT * FROM drive_movement_observations ORDER BY startEpochMillis ASC")
    fun observeObservations(): Flow<List<DriveMovementObservationEntity>>

    @Upsert
    suspend fun upsert(entity: DriveMovementObservationEntity)

    @Query("DELETE FROM drive_movement_observations WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
