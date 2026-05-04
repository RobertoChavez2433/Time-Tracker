package com.robertochavez.timetracker.data

import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import com.robertochavez.timetracker.core.common.repository.LocalDataResetter
import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import com.robertochavez.timetracker.core.database.TimeTrackerDatabase
import com.robertochavez.timetracker.core.location.activity.ActivityTransitionRegistrar
import com.robertochavez.timetracker.core.location.geofence.HomeGeofenceRegistrar
import com.robertochavez.timetracker.core.location.geofence.WorkGeofenceRegistrar
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLocalDataResetter @Inject constructor(
    private val database: TimeTrackerDatabase,
    private val appSettingsRepository: AppSettingsRepository,
    private val workLocationRepository: WorkLocationRepository,
    private val homeGeofenceRegistrar: HomeGeofenceRegistrar,
    private val workGeofenceRegistrar: WorkGeofenceRegistrar,
    private val activityTransitionRegistrar: ActivityTransitionRegistrar,
    private val logger: AppLogger,
) : LocalDataResetter {
    override suspend fun deleteAllLocalData() {
        logger.info(LogCategory.SETTINGS, "Local data reset started")
        val workLocations = runCatching { workLocationRepository.getWorkLocations() }.getOrDefault(emptyList())
        runCatching { homeGeofenceRegistrar.unregisterHomeGeofence() }
        runCatching { workGeofenceRegistrar.unregisterWorkGeofences(workLocations) }
        runCatching { workGeofenceRegistrar.unregisterWorkGeofence() }
        runCatching { activityTransitionRegistrar.unregisterDriveAndIdleTransitions() }
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        appSettingsRepository.resetSettings()
        logger.info(LogCategory.SETTINGS, "Local data reset finished")
    }
}
