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
    registerTimeTrackerGeofences(
        context = context,
        specs = listOf(TimeTrackerGeofenceSpec(requestId, latitude, longitude, radiusMeters)),
    )
}

@SuppressLint("MissingPermission")
internal suspend fun GeofencingClient.registerTimeTrackerGeofences(context: Context, specs: List<TimeTrackerGeofenceSpec>) {
    if (specs.isEmpty()) return
    addGeofences(
        buildTimeTrackerGeofencingRequest(specs),
        timeTrackerGeofencePendingIntent(context),
    ).awaitTask()
}

internal suspend fun GeofencingClient.unregisterTimeTrackerGeofence(requestId: String) {
    removeGeofences(listOf(requestId)).awaitTask()
}

internal suspend fun GeofencingClient.unregisterTimeTrackerGeofences(requestIds: List<String>) {
    if (requestIds.isEmpty()) return
    removeGeofences(requestIds).awaitTask()
}

internal data class TimeTrackerGeofenceSpec(val requestId: String, val latitude: Double, val longitude: Double, val radiusMeters: Float)

private fun buildTimeTrackerGeofencingRequest(specs: List<TimeTrackerGeofenceSpec>): GeofencingRequest = GeofencingRequest.Builder()
    .setInitialTrigger(
        GeofencingRequest.INITIAL_TRIGGER_ENTER or
            GeofencingRequest.INITIAL_TRIGGER_EXIT or
            GeofencingRequest.INITIAL_TRIGGER_DWELL,
    )
    .addGeofences(specs.map { it.toGeofence() })
    .build()

private fun TimeTrackerGeofenceSpec.toGeofence(): Geofence = Geofence.Builder()
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
