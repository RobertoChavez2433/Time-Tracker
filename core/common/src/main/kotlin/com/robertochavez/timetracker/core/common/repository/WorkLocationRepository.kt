package com.robertochavez.timetracker.core.common.repository

import com.robertochavez.timetracker.core.common.model.WorkLocation
import kotlinx.coroutines.flow.Flow

interface WorkLocationRepository {
    fun observeWorkLocation(): Flow<WorkLocation?>

    fun observeWorkLocations(): Flow<List<WorkLocation>>

    suspend fun getWorkLocation(): WorkLocation?

    suspend fun getWorkLocations(): List<WorkLocation>

    suspend fun setWorkLocation(workLocation: WorkLocation)
}
