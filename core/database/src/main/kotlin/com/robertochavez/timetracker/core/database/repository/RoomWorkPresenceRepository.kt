package com.robertochavez.timetracker.core.database.repository

import com.robertochavez.timetracker.core.common.model.WorkPresence
import com.robertochavez.timetracker.core.common.repository.WorkPresenceRepository
import com.robertochavez.timetracker.core.database.dao.WorkPresenceDao
import com.robertochavez.timetracker.core.database.entity.WorkPresenceEntity
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWorkPresenceRepository @Inject constructor(private val workPresenceDao: WorkPresenceDao, private val logger: AppLogger) :
    WorkPresenceRepository {
    override fun observeWorkPresence(): Flow<WorkPresence> = workPresenceDao.observeWorkPresence().map {
        it?.toModel() ?: defaultPresence()
    }

    override suspend fun getWorkPresence(): WorkPresence = workPresenceDao.getWorkPresence()?.toModel() ?: defaultPresence()

    override suspend fun setAtWork(atWork: Boolean, updatedAt: Instant) {
        workPresenceDao.upsert(WorkPresenceEntity.fromModel(WorkPresence(atWork = atWork, updatedAt = updatedAt)))
        logger.info(LogCategory.LOCATION, "Work presence changed", mapOf("atWork" to atWork))
    }

    private fun defaultPresence(): WorkPresence = WorkPresence(atWork = false, updatedAt = Instant.EPOCH)
}
