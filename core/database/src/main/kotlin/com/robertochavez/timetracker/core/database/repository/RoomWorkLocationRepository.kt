package com.robertochavez.timetracker.core.database.repository

import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import com.robertochavez.timetracker.core.database.dao.WorkLocationDao
import com.robertochavez.timetracker.core.database.entity.WorkLocationEntity
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWorkLocationRepository @Inject constructor(private val workLocationDao: WorkLocationDao, private val logger: AppLogger) :
    WorkLocationRepository {
    override fun observeWorkLocation(): Flow<WorkLocation?> = workLocationDao.observeWorkLocation().map { it?.toModel() }

    override fun observeWorkLocations(): Flow<List<WorkLocation>> =
        workLocationDao.observeWorkLocations().map { locations -> locations.map { it.toModel() } }

    override suspend fun getWorkLocation(): WorkLocation? = workLocationDao.getWorkLocation()?.toModel()

    override suspend fun getWorkLocation(id: String): WorkLocation? = workLocationDao.getWorkLocationById(id)?.toModel()

    override suspend fun getWorkLocations(): List<WorkLocation> = workLocationDao.getWorkLocations().map { it.toModel() }

    override suspend fun setWorkLocation(workLocation: WorkLocation) {
        workLocationDao.upsert(WorkLocationEntity.fromModel(workLocation))
        logger.info(
            LogCategory.LOCATION,
            "Work location saved",
            mapOf("id" to workLocation.id, "label" to workLocation.label, "radiusMeters" to workLocation.radiusMeters),
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
