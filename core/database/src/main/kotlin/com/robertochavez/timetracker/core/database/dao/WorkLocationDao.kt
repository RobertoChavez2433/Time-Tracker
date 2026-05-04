package com.robertochavez.timetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.robertochavez.timetracker.core.database.entity.WorkLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkLocationDao {
    @Query("SELECT * FROM work_locations ORDER BY updatedAtEpochMillis DESC LIMIT 1")
    fun observeWorkLocation(): Flow<WorkLocationEntity?>

    @Query("SELECT * FROM work_locations ORDER BY updatedAtEpochMillis DESC LIMIT 1")
    suspend fun getWorkLocation(): WorkLocationEntity?

    @Query("SELECT * FROM work_locations ORDER BY updatedAtEpochMillis DESC")
    fun observeWorkLocations(): Flow<List<WorkLocationEntity>>

    @Query("SELECT * FROM work_locations ORDER BY updatedAtEpochMillis DESC")
    suspend fun getWorkLocations(): List<WorkLocationEntity>

    @Upsert
    suspend fun upsert(entity: WorkLocationEntity)
}
