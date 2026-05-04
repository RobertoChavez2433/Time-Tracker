package com.robertochavez.timetracker.core.location.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.robertochavez.timetracker.core.location.awaitTask
import com.robertochavez.timetracker.core.location.hasBackgroundLocationPermission
import com.robertochavez.timetracker.core.location.hasFineLocationPermission

internal fun Context.requireGeofencePermissions(preciseMessage: String, backgroundMessage: String) {
    if (!hasFineLocationPermission()) {
        error(preciseMessage)
    }
    if (!hasBackgroundLocationPermission()) {
        error(backgroundMessage)
    }
}

@SuppressLint("MissingPermission")
internal suspend fun GeofencingClient.registerTimeTrackerGeofence(
    context: Context,
    requestId: String,
    latitude: Double,
    longitude: Double,
    radiusMeters: Float,
) {
    addGeofences(
        buildTimeTrackerGeofencingRequest(
            requestId = requestId,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
        ),
        timeTrackerGeofencePendingIntent(context),
    ).awaitTask()
}

internal suspend fun GeofencingClient.unregisterTimeTrackerGeofence(requestId: String) {
    removeGeofences(listOf(requestId)).awaitTask()
}

private fun buildTimeTrackerGeofencingRequest(
    requestId: String,
    latitude: Double,
    longitude: Double,
    radiusMeters: Float,
): GeofencingRequest {
    val geofence = Geofence.Builder()
        .setRequestId(requestId)
        .setCircularRegion(latitude, longitude, radiusMeters)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setLoiteringDelay(TimeTrackerGeofenceIds.DWELL_DELAY_MILLIS)
        .setTransitionTypes(
            Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_EXIT or
                Geofence.GEOFENCE_TRANSITION_DWELL,
        )
        .build()

    return GeofencingRequest.Builder()
        .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
        .addGeofence(geofence)
        .build()
}

private fun timeTrackerGeofencePendingIntent(context: Context): PendingIntent {
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_IMMUTABLE
        }
    val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
    return PendingIntent.getBroadcast(context, TimeTrackerGeofenceIds.PENDING_INTENT_REQUEST_CODE, intent, flags)
}
