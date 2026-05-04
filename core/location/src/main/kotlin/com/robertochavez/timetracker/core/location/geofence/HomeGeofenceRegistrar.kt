package com.robertochavez.timetracker.core.location.geofence

import android.content.Context
import com.google.android.gms.location.GeofencingClient
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.logging.warn
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface HomeGeofenceRegistrar {
    suspend fun registerHomeGeofence(homeLocation: HomeLocation)

    suspend fun unregisterHomeGeofence()
}

@Singleton
class PlayServicesHomeGeofenceRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geofencingClient: GeofencingClient,
    private val logger: AppLogger,
) : HomeGeofenceRegistrar {
    override suspend fun registerHomeGeofence(homeLocation: HomeLocation) {
        context.requireGeofencePermissions(
            preciseMessage = "Precise location is required to register the home geofence. " +
                "Approximate location can miss home enter and exit events.",
            backgroundMessage = "Allow all the time location access before enabling automatic home enter and exit detection.",
        )
        unregisterHomeGeofence()
        try {
            geofencingClient.registerTimeTrackerGeofence(
                context = context,
                requestId = TimeTrackerGeofenceIds.HOME,
                latitude = homeLocation.latitude,
                longitude = homeLocation.longitude,
                radiusMeters = homeLocation.radiusMeters,
            )
            logger.info(LogCategory.LOCATION, "Home geofence registered", mapOf("radiusMeters" to homeLocation.radiusMeters))
        } catch (error: SecurityException) {
            // Permission can be revoked after the preflight check.
            logger.warn(LogCategory.LOCATION, "Home geofence registration lost permission", error = error)
        }
    }

    override suspend fun unregisterHomeGeofence() {
        try {
            geofencingClient.unregisterTimeTrackerGeofence(TimeTrackerGeofenceIds.HOME)
            logger.info(LogCategory.LOCATION, "Home geofence unregistered")
        } catch (error: SecurityException) {
            // Permission can be revoked after the preflight check.
            logger.warn(LogCategory.LOCATION, "Home geofence unregister lost permission", error = error)
        }
    }
}
