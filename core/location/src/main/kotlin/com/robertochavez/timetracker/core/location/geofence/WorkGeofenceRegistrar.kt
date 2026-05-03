package com.robertochavez.timetracker.core.location.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.location.awaitTask
import com.robertochavez.timetracker.core.location.hasBackgroundLocationPermission
import com.robertochavez.timetracker.core.location.hasFineLocationPermission
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
    @SuppressLint("MissingPermission")
    override suspend fun registerWorkGeofence(workLocation: WorkLocation) {
        if (!context.hasFineLocationPermission()) {
            error("Precise location is required to register the work geofence.")
        }
        if (!context.hasBackgroundLocationPermission()) {
            error("Allow all the time location access before enabling automatic work enter and exit detection.")
        }
        unregisterWorkGeofence()
        val geofence = Geofence.Builder()
            .setRequestId(TimeTrackerGeofenceIds.WORK)
            .setCircularRegion(workLocation.latitude, workLocation.longitude, workLocation.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setLoiteringDelay(TimeTrackerGeofenceIds.DWELL_DELAY_MILLIS)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT or
                    Geofence.GEOFENCE_TRANSITION_DWELL,
            )
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofence(geofence)
            .build()

        try {
            geofencingClient.addGeofences(request, pendingIntent()).awaitTask()
            logger.info(LogCategory.LOCATION, "Work geofence registered", mapOf("radiusMeters" to workLocation.radiusMeters))
        } catch (error: SecurityException) {
            logger.warn(LogCategory.LOCATION, "Work geofence registration lost permission", error = error)
        }
    }

    override suspend fun unregisterWorkGeofence() {
        try {
            geofencingClient.removeGeofences(listOf(TimeTrackerGeofenceIds.WORK)).awaitTask()
            logger.info(LogCategory.LOCATION, "Work geofence unregistered")
        } catch (error: SecurityException) {
            logger.warn(LogCategory.LOCATION, "Work geofence unregister lost permission", error = error)
        }
    }

    private fun pendingIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_IMMUTABLE
            }
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(context, TimeTrackerGeofenceIds.PENDING_INTENT_REQUEST_CODE, intent, flags)
    }
}
