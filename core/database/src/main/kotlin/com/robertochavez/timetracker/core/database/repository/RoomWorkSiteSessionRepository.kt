package com.robertochavez.timetracker.core.database.repository

import androidx.room.withTransaction
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import com.robertochavez.timetracker.core.common.repository.WorkSiteSessionRepository
import com.robertochavez.timetracker.core.database.TimeTrackerDatabase
import com.robertochavez.timetracker.core.database.dao.WorkSiteSessionDao
import com.robertochavez.timetracker.core.database.entity.WorkSiteSessionEntity
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWorkSiteSessionRepository @Inject constructor(
    private val database: TimeTrackerDatabase,
    private val workSiteSessionDao: WorkSiteSessionDao,
    private val logger: AppLogger,
) : WorkSiteSessionRepository {
    override fun observeWorkSiteSessions(): Flow<List<WorkSiteSession>> =
        workSiteSessionDao.observeWorkSiteSessions().map { sessions -> sessions.map(WorkSiteSessionEntity::toModel) }

    override suspend fun startWorkSiteSession(workLocationId: String, at: Instant): WorkSiteSession = database.withTransaction {
        val existing = workSiteSessionDao.getActiveForLocation(workLocationId)?.toModel()
        if (existing != null) return@withTransaction existing

        WorkSiteSession(
            id = UUID.randomUUID().toString(),
            workLocationId = workLocationId,
            start = at,
            end = null,
        ).also { session ->
            workSiteSessionDao.upsert(WorkSiteSessionEntity.fromModel(session))
            logger.info(
                LogCategory.LOCATION,
                "Work site session started",
                mapOf("workLocationId" to workLocationId, "session" to session.id.safePrefix()),
            )
        }
    }

    override suspend fun stopActiveWorkSiteSession(workLocationId: String?, at: Instant): WorkSiteSession? = database.withTransaction {
        val active = if (workLocationId == null) {
            workSiteSessionDao.getActive()
        } else {
            workSiteSessionDao.getActiveForLocation(workLocationId)
        } ?: return@withTransaction null

        if (at.toEpochMilli() <= active.startEpochMillis) {
            workSiteSessionDao.deleteById(active.id)
            return@withTransaction null
        }

        val stopped = active.toModel().copy(end = at)
        workSiteSessionDao.upsert(WorkSiteSessionEntity.fromModel(stopped))
        logger.info(
            LogCategory.LOCATION,
            "Work site session stopped",
            mapOf("workLocationId" to active.workLocationId, "session" to active.id.safePrefix()),
        )
        stopped
    }
}

private fun String.safePrefix(): String = take(8)
