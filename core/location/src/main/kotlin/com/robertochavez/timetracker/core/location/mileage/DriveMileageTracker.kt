package com.robertochavez.timetracker.core.location.mileage

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Build
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.robertochavez.timetracker.core.common.domain.DriveMileageCalculator
import com.robertochavez.timetracker.core.common.domain.DriveMileagePoint
import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import com.robertochavez.timetracker.core.location.awaitTask
import com.robertochavez.timetracker.core.location.hasBackgroundLocationPermission
import com.robertochavez.timetracker.core.location.hasFineLocationPermission
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.logging.warn
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface DriveMileageTracker {
    suspend fun startTracking()

    suspend fun stopTracking()

    suspend fun clearBaseline()

    suspend fun recordLocation(location: Location)
}

@Singleton
class PlayServicesDriveMileageTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    private val trackingSessionController: TrackingSessionController,
    private val clock: Clock,
    private val logger: AppLogger,
) : DriveMileageTracker {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @SuppressLint("MissingPermission")
    override suspend fun startTracking() {
        if (!context.hasFineLocationPermission() || !context.hasBackgroundLocationPermission()) {
            logger.info(LogCategory.LOCATION, "Drive mileage tracking skipped because location permission is missing")
            return
        }

        clearBaseline()
        preferences.edit().putBoolean(KEY_ACTIVE, true).apply()
        try {
            fusedLocationProviderClient
                .requestLocationUpdates(locationRequest(), pendingIntent())
                .awaitTask()
            logger.info(LogCategory.LOCATION, "Drive mileage location updates started")
        } catch (error: SecurityException) {
            preferences.edit().putBoolean(KEY_ACTIVE, false).apply()
            logger.warn(LogCategory.LOCATION, "Drive mileage tracking lost location permission", error = error)
        }
    }

    override suspend fun stopTracking() {
        preferences.edit().putBoolean(KEY_ACTIVE, false).clearLastPoint().apply()
        try {
            fusedLocationProviderClient.removeLocationUpdates(pendingIntent()).awaitTask()
            logger.info(LogCategory.LOCATION, "Drive mileage location updates stopped")
        } catch (error: SecurityException) {
            logger.warn(LogCategory.LOCATION, "Drive mileage stop lost location permission", error = error)
        }
    }

    override suspend fun clearBaseline() {
        preferences.edit().clearLastPoint().apply()
        logger.info(LogCategory.LOCATION, "Drive mileage baseline cleared")
    }

    override suspend fun recordLocation(location: Location) {
        if (!preferences.getBoolean(KEY_ACTIVE, false)) return

        val current = location.toMileagePoint()
        val result = DriveMileageCalculator.calculate(preferences.lastPoint(), current)
        if (result.distanceMeters > 0.0) {
            val session = trackingSessionController.addDrivenDistanceToActiveSession(result.distanceMeters, current.at)
            if (session == null) {
                logger.info(LogCategory.LOCATION, "Drive mileage segment ignored because no away session is active")
            }
        }
        preferences.edit().putLastPoint(result.baseline).apply()
        logger.info(
            LogCategory.LOCATION,
            "Drive mileage location processed",
            mapOf(
                "distanceMeters" to result.distanceMeters,
                "hasBaseline" to (result.baseline != null),
                "accuracyMeters" to current.accuracyMeters,
            ),
        )
    }

    private fun locationRequest(): LocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MILLIS)
        .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MILLIS)
        .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
        .setMaxUpdateDelayMillis(MAX_UPDATE_DELAY_MILLIS)
        .build()

    private fun pendingIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_IMMUTABLE
            }
        val intent = Intent(context, DriveLocationBroadcastReceiver::class.java).setAction(ACTION_DRIVE_LOCATION_UPDATE)
        return PendingIntent.getBroadcast(context, DRIVE_LOCATION_PENDING_INTENT_REQUEST_CODE, intent, flags)
    }

    private fun Location.toMileagePoint(): DriveMileagePoint = DriveMileagePoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = takeIf { hasAccuracy() }?.accuracy,
        at = time.takeIf { it > 0L }?.let(Instant::ofEpochMilli) ?: Instant.now(clock),
    )

    private companion object {
        const val PREFERENCES_NAME = "drive_mileage_tracker"
        const val ACTION_DRIVE_LOCATION_UPDATE = "com.robertochavez.timetracker.action.DRIVE_LOCATION_UPDATE"
        const val DRIVE_LOCATION_PENDING_INTENT_REQUEST_CODE = 4300
        const val UPDATE_INTERVAL_MILLIS = 15_000L
        const val MIN_UPDATE_INTERVAL_MILLIS = 5_000L
        const val MAX_UPDATE_DELAY_MILLIS = 30_000L
        const val MIN_UPDATE_DISTANCE_METERS = 20f
    }
}

private fun SharedPreferences.lastPoint(): DriveMileagePoint? {
    val latitude = getString(KEY_LAST_LATITUDE, null)?.toDoubleOrNull()
    val longitude = getString(KEY_LAST_LONGITUDE, null)?.toDoubleOrNull()
    val atEpochMillis = getLong(KEY_LAST_AT_EPOCH_MILLIS, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
    return if (latitude == null || longitude == null || atEpochMillis == null) {
        null
    } else {
        DriveMileagePoint(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = getFloat(KEY_LAST_ACCURACY, Float.NaN).takeUnless { it.isNaN() },
            at = Instant.ofEpochMilli(atEpochMillis),
        )
    }
}

private fun SharedPreferences.Editor.putLastPoint(point: DriveMileagePoint?): SharedPreferences.Editor {
    if (point == null) return clearLastPoint()
    putString(KEY_LAST_LATITUDE, point.latitude.toString())
    putString(KEY_LAST_LONGITUDE, point.longitude.toString())
    putLong(KEY_LAST_AT_EPOCH_MILLIS, point.at.toEpochMilli())
    val accuracyMeters = point.accuracyMeters
    if (accuracyMeters == null) {
        remove(KEY_LAST_ACCURACY)
    } else {
        putFloat(KEY_LAST_ACCURACY, accuracyMeters)
    }
    return this
}

private fun SharedPreferences.Editor.clearLastPoint(): SharedPreferences.Editor {
    remove(KEY_LAST_LATITUDE)
    remove(KEY_LAST_LONGITUDE)
    remove(KEY_LAST_ACCURACY)
    remove(KEY_LAST_AT_EPOCH_MILLIS)
    return this
}

private const val KEY_ACTIVE = "active"
private const val KEY_LAST_LATITUDE = "last_latitude"
private const val KEY_LAST_LONGITUDE = "last_longitude"
private const val KEY_LAST_ACCURACY = "last_accuracy"
private const val KEY_LAST_AT_EPOCH_MILLIS = "last_at_epoch_millis"
