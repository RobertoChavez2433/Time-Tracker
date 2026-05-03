package com.robertochavez.timetracker.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.robertochavez.timetracker.core.database.entity.WorkScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkScheduleDao {
    @Query("SELECT * FROM work_schedule_days ORDER BY dayOfWeek ASC")
    fun observeWorkScheduleDays(): Flow<List<WorkScheduleEntity>>

    @Query("SELECT * FROM work_schedule_days ORDER BY dayOfWeek ASC")
    suspend fun getWorkScheduleDays(): List<WorkScheduleEntity>

    @Upsert
    suspend fun upsertAll(days: List<WorkScheduleEntity>)
}
