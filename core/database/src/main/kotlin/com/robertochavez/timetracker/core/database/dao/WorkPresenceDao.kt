package com.robertochavez.timetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.robertochavez.timetracker.core.database.entity.WorkPresenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkPresenceDao {
    @Query("SELECT * FROM work_presence WHERE id = :id LIMIT 1")
    fun observeWorkPresence(id: String = WorkPresenceEntity.PRESENCE_ID): Flow<WorkPresenceEntity?>

    @Query("SELECT * FROM work_presence WHERE id = :id LIMIT 1")
    suspend fun getWorkPresence(id: String = WorkPresenceEntity.PRESENCE_ID): WorkPresenceEntity?

    @Upsert
    suspend fun upsert(entity: WorkPresenceEntity)
}
