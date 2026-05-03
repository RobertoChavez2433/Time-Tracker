package com.robertochavez.timetracker.data

import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import com.robertochavez.timetracker.core.common.repository.LocalDataResetter
import com.robertochavez.timetracker.core.database.TimeTrackerDatabase
import com.robertochavez.timetracker.core.location.activity.ActivityTransitionRegistrar
import com.robertochavez.timetracker.core.location.geofence.HomeGeofenceRegistrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLocalDataResetter @Inject constructor(
    private val database: TimeTrackerDatabase,
    private val appSettingsRepository: AppSettingsRepository,
    private val homeGeofenceRegistrar: HomeGeofenceRegistrar,
    private val activityTransitionRegistrar: ActivityTransitionRegistrar,
) : LocalDataResetter {
    override suspend fun deleteAllLocalData() {
        runCatching { homeGeofenceRegistrar.unregisterHomeGeofence() }
        runCatching { activityTransitionRegistrar.unregisterDriveAndIdleTransitions() }
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        appSettingsRepository.resetSettings()
    }
}
