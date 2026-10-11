package com.robertochavez.timetracker.testing

import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.database.TimeTrackerDatabase
import com.robertochavez.timetracker.core.location.activity.ActivityClassificationCoordinator
import com.robertochavez.timetracker.core.location.mileage.DriveMileageTracker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ClassificationTestEntryPoint {
    fun tracking(): TrackingRepository
    fun database(): TimeTrackerDatabase
    fun classification(): ActivityClassificationCoordinator
    fun mileage(): DriveMileageTracker
}
