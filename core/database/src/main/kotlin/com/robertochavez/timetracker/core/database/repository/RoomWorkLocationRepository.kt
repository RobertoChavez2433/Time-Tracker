package com.robertochavez.timetracker.core.database.repository

import androidx.room.withTransaction
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import com.robertochavez.timetracker.core.database.TimeTrackerDatabase
import com.robertochavez.timetracker.core.database.dao.WorkLocationDao
import com.robertochavez.timetracker.core.database.dao.WorkSiteSessionDao
import com.robertochavez.timetracker.core.database.entity.WorkLocationEntity
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWorkLocationRepository @Inject constructor(
    private val database: TimeTrackerDatabase,
    private val workLocationDao: WorkLocationDao,
    private val workSiteSessionDao: WorkSiteSessionDao,
    private val logger: AppLogger,
) : WorkLocationRepository {
    override fun observeWorkLocation(): Flow<WorkLocation?> = workLocationDao.observeWorkLocation().map { it?.toModel() }

    override fun observeWorkLocations(): Flow<List<WorkLocation>> =
        workLocationDao.observeWorkLocations().map { locations -> locations.map { it.toModel() } }

    override suspend fun getWorkLocation(): WorkLocation? = workLocationDao.getWorkLocation()?.toModel()

    override suspend fun getWorkLocation(id: String): WorkLocation? = workLocationDao.getWorkLocationById(id)?.toModel()

    override suspend fun getWorkLocations(): List<WorkLocation> = workLocationDao.getWorkLocations().map { it.toModel() }

    override suspend fun setWorkLocation(workLocation: WorkLocation) {
        database.withTransaction {
            val previous = workLocationDao.getWorkLocationById(workLocation.id)
            workLocationDao.upsert(WorkLocationEntity.fromModel(workLocation))
            val projectNumber = workLocation.projectNumber
            if (previous?.projectNumber == null && projectNumber != null) {
                workSiteSessionDao.backfillNullProjectNumberSnapshots(workLocation.id, projectNumber)
            }
        }
        logger.info(
            LogCategory.LOCATION,
            "Work location saved",
            mapOf(
                "id" to workLocation.id,
                "label" to workLocation.label,
                "projectNumber" to (workLocation.projectNumber ?: "unassigned"),
                "radiusMeters" to workLocation.radiusMeters,
            ),
        )
    }

    override suspend fun renameWorkLocation(id: String, label: String): WorkLocation? {
        val trimmedLabel = label.trim()
        val existing = getWorkLocation(id) ?: return null
        val renamed = existing.copy(label = trimmedLabel)
        workLocationDao.upsert(WorkLocationEntity.fromModel(renamed))
        logger.info(LogCategory.LOCATION, "Work location renamed", mapOf("id" to id, "label" to trimmedLabel))
        return renamed
    }
}
