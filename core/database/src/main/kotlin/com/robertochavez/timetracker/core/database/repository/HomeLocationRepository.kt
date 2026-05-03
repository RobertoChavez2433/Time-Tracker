package com.robertochavez.timetracker.core.database.repository

import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.database.dao.HomeLocationDao
import com.robertochavez.timetracker.core.database.entity.HomeLocationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeLocationRepository @Inject constructor(
    private val homeLocationDao: HomeLocationDao,
) {
    fun observeHomeLocation(): Flow<HomeLocation?> = homeLocationDao.observeHomeLocation().map { it?.toModel() }

    suspend fun getHomeLocation(): HomeLocation? = homeLocationDao.getHomeLocation()?.toModel()

    suspend fun setHomeLocation(homeLocation: HomeLocation) {
        homeLocationDao.upsert(HomeLocationEntity.fromModel(homeLocation))
    }
}
