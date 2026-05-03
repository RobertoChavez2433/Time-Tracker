package com.robertochavez.timetracker.core.database.repository

import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.repository.HomeLocationRepository
import com.robertochavez.timetracker.core.database.dao.HomeLocationDao
import com.robertochavez.timetracker.core.database.entity.HomeLocationEntity
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHomeLocationRepository @Inject constructor(private val homeLocationDao: HomeLocationDao, private val logger: AppLogger) :
    HomeLocationRepository {
    override fun observeHomeLocation(): Flow<HomeLocation?> = homeLocationDao.observeHomeLocation().map { it?.toModel() }

    override suspend fun getHomeLocation(): HomeLocation? = homeLocationDao.getHomeLocation()?.toModel()

    override suspend fun setHomeLocation(homeLocation: HomeLocation) {
        homeLocationDao.upsert(HomeLocationEntity.fromModel(homeLocation))
        logger.info(LogCategory.LOCATION, "Home location saved", mapOf("radiusMeters" to homeLocation.radiusMeters))
    }
}
