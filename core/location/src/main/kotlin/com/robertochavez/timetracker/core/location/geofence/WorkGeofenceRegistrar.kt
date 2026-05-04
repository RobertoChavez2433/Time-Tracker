package com.robertochavez.timetracker.core.location.geofence

import android.content.Context
import com.google.android.gms.location.GeofencingClient
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.logging.warn
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface WorkGeofenceRegistrar {
    suspend fun registerWorkGeofence(workLocation: WorkLocation)

    suspend fun unregisterWorkGeofence()
}

@Singleton
class PlayServicesWorkGeofenceRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geofencingClient: GeofencingClient,
    private val logger: AppLogger,
) : WorkGeofenceRegistrar {
    override suspend fun registerWorkGeofence(workLocation: WorkLocation) {
        context.requireGeofencePermissions(
            preciseMessage = "Precise location is required to register the work geofence.",
            backgroundMessage = "Allow all the time location access before enabling automatic work enter and exit detection.",
        )
        unregisterWorkGeofence()
        try {
            geofencingClient.registerTimeTrackerGeofence(
                context = context,
                requestId = TimeTrackerGeofenceIds.WORK,
                latitude = workLocation.latitude,
                longitude = workLocation.longitude,
                radiusMeters = workLocation.radiusMeters,
            )
            logger.info(LogCategory.LOCATION, "Work geofence registered", mapOf("radiusMeters" to workLocation.radiusMeters))
        } catch (error: SecurityException) {
            logger.warn(LogCategory.LOCATION, "Work geofence registration lost permission", error = error)
        }
    }

    override suspend fun unregisterWorkGeofence() {
        try {
            geofencingClient.unregisterTimeTrackerGeofence(TimeTrackerGeofenceIds.WORK)
            logger.info(LogCategory.LOCATION, "Work geofence unregistered")
        } catch (error: SecurityException) {
            logger.warn(LogCategory.LOCATION, "Work geofence unregister lost permission", error = error)
        }
    }
}
