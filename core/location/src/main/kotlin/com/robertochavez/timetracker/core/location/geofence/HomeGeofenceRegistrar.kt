package com.robertochavez.timetracker.core.location.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.location.awaitTask
import com.robertochavez.timetracker.core.location.hasFineLocationPermission
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
) : HomeGeofenceRegistrar {
    @SuppressLint("MissingPermission")
    override suspend fun registerHomeGeofence(homeLocation: HomeLocation) {
        if (!context.hasFineLocationPermission()) {
            return
        }
        unregisterHomeGeofence()
        val geofence = Geofence.Builder()
            .setRequestId(HOME_GEOFENCE_ID)
            .setCircularRegion(homeLocation.latitude, homeLocation.longitude, homeLocation.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setLoiteringDelay(DWELL_DELAY_MILLIS)
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
        } catch (_: SecurityException) {
            // Permission can be revoked after the preflight check.
        }
    }

    override suspend fun unregisterHomeGeofence() {
        try {
            geofencingClient.removeGeofences(listOf(HOME_GEOFENCE_ID)).awaitTask()
        } catch (_: SecurityException) {
            // Permission can be revoked after the preflight check.
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
        return PendingIntent.getBroadcast(context, GEOFENCE_PENDING_INTENT_REQUEST_CODE, intent, flags)
    }

    companion object {
        const val HOME_GEOFENCE_ID = "home"
        const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 4100
        const val DWELL_DELAY_MILLIS = 2 * 60 * 1000
    }
}
