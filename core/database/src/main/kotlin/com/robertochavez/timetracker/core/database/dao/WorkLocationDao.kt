package com.robertochavez.timetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.robertochavez.timetracker.core.database.entity.WorkLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkLocationDao {
    @Query("SELECT * FROM work_locations WHERE id = :id LIMIT 1")
    fun observeWorkLocation(id: String = WorkLocationEntity.WORK_ID): Flow<WorkLocationEntity?>

    @Query("SELECT * FROM work_locations WHERE id = :id LIMIT 1")
    suspend fun getWorkLocation(id: String = WorkLocationEntity.WORK_ID): WorkLocationEntity?

    @Upsert
    suspend fun upsert(entity: WorkLocationEntity)
}
