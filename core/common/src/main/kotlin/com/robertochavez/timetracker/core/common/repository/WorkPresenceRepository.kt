package com.robertochavez.timetracker.core.common.repository

import com.robertochavez.timetracker.core.common.model.WorkPresence
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface WorkPresenceRepository {
    fun observeWorkPresence(): Flow<WorkPresence>

    suspend fun getWorkPresence(): WorkPresence

    suspend fun setAtWork(atWork: Boolean, updatedAt: Instant)
}
