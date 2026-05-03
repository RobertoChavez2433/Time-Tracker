package com.robertochavez.timetracker.core.common.repository

import com.robertochavez.timetracker.core.common.model.WorkSchedule
import kotlinx.coroutines.flow.Flow

interface WorkScheduleRepository {
    fun observeWorkSchedule(): Flow<WorkSchedule>

    suspend fun getWorkSchedule(): WorkSchedule

    suspend fun setWorkSchedule(schedule: WorkSchedule)
}
