package com.robertochavez.timetracker.core.location

import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import com.robertochavez.timetracker.core.common.repository.HomeLocationRepository
import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import com.robertochavez.timetracker.core.location.activity.ActivityTransitionRegistrar
import com.robertochavez.timetracker.core.location.geofence.HomeGeofenceRegistrar
import com.robertochavez.timetracker.core.location.geofence.WorkGeofenceRegistrar
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.logging.warn
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

interface TrackingRegistrationSynchronizer {
    suspend fun synchronize(reason: String)
}

@Singleton
class PlayServicesTrackingRegistrationSynchronizer @Inject constructor(
    private val homeLocationRepository: HomeLocationRepository,
    private val workLocationRepository: WorkLocationRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val homeGeofenceRegistrar: HomeGeofenceRegistrar,
    private val workGeofenceRegistrar: WorkGeofenceRegistrar,
    private val activityTransitionRegistrar: ActivityTransitionRegistrar,
    private val logger: AppLogger,
) : TrackingRegistrationSynchronizer {
    override suspend fun synchronize(reason: String) {
        val homeRegistered = registerHome(reason)
        val workRegisteredCount = registerWork(reason)
        val activityRegistered = registerActivityTransitionsIfEnabled(reason)

        logger.info(
            LogCategory.LOCATION,
            "Tracking registrations synchronized",
            mapOf(
                "reason" to reason,
                "homeRegistered" to homeRegistered,
                "workRegisteredCount" to workRegisteredCount,
                "activityRegistered" to activityRegistered,
            ),
        )
    }

    private suspend fun registerHome(reason: String): Boolean {
        val home = homeLocationRepository.getHomeLocation() ?: return false
        return runCatching {
            homeGeofenceRegistrar.registerHomeGeofence(home)
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                logger.warn(
                    LogCategory.LOCATION,
                    "Home geofence sync failed",
                    mapOf("reason" to reason),
                    error,
                )
                false
            },
        )
    }

    private suspend fun registerWork(reason: String): Int {
        val workLocations = workLocationRepository.getWorkLocations()
        if (workLocations.isEmpty()) return 0

        return runCatching {
            workGeofenceRegistrar.registerWorkGeofences(workLocations)
        }.fold(
            onSuccess = { workLocations.size },
            onFailure = { error ->
                logger.warn(
                    LogCategory.LOCATION,
                    "Work geofence sync failed",
                    mapOf("reason" to reason, "count" to workLocations.size),
                    error,
                )
                0
            },
        )
    }

    private suspend fun registerActivityTransitionsIfEnabled(reason: String): Boolean {
        val settings = appSettingsRepository.settings.first()
        if (!settings.activityDetectionEnabled) return false

        return runCatching {
            activityTransitionRegistrar.registerDriveAndIdleTransitions()
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                logger.warn(
                    LogCategory.ACTIVITY,
                    "Activity transition sync failed",
                    mapOf("reason" to reason),
                    error,
                )
                false
            },
        )
    }
}
