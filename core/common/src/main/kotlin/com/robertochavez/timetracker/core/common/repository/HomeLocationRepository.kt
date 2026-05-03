package com.robertochavez.timetracker.core.common.repository

import com.robertochavez.timetracker.core.common.model.HomeLocation
import kotlinx.coroutines.flow.Flow

interface HomeLocationRepository {
    fun observeHomeLocation(): Flow<HomeLocation?>

    suspend fun getHomeLocation(): HomeLocation?

    suspend fun setHomeLocation(homeLocation: HomeLocation)
}
