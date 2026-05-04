package com.robertochavez.timetracker.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.logging.warn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface CurrentGeofenceLocationProvider {
    suspend fun currentPreciseHomeLocation(radiusMeters: Float = HomeLocation.MINIMUM_RADIUS_METERS): HomeLocation?

    suspend fun currentPreciseWorkLocation(radiusMeters: Float = WorkLocation.MINIMUM_RADIUS_METERS): WorkLocation?
}

@Singleton
class PlayServicesCurrentGeofenceLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    private val clock: Clock,
    private val logger: AppLogger,
) : CurrentGeofenceLocationProvider {
    override suspend fun currentPreciseHomeLocation(radiusMeters: Float): HomeLocation? = currentPreciseLocation()?.let {
        HomeLocation(
            latitude = it.latitude,
            longitude = it.longitude,
            radiusMeters = radiusMeters,
            updatedAt = Instant.now(clock),
        )
    }

    override suspend fun currentPreciseWorkLocation(radiusMeters: Float): WorkLocation? = currentPreciseLocation()?.let {
        WorkLocation(
            latitude = it.latitude,
            longitude = it.longitude,
            radiusMeters = radiusMeters,
            updatedAt = Instant.now(clock),
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentPreciseLocation(): Location? {
        if (!context.hasFineLocationPermission()) {
            logger.info(LogCategory.LOCATION, "Current location skipped because fine location is missing")
            return null
        }

        val location = requestCurrentLocation() ?: requestLastKnownLocation()
        logLocationResult(location)
        return location
    }

    private suspend fun requestCurrentLocation(): Location? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MILLIS)
            .build()
        val cancellationTokenSource = CancellationTokenSource()
        val location = try {
            withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MILLIS) {
                fusedLocationProviderClient
                    .getCurrentLocation(request, cancellationTokenSource.token)
                    .awaitTask()
            }.also {
                if (it == null) {
                    cancellationTokenSource.cancel()
                    logger.warn(LogCategory.LOCATION, "Current location request timed out")
                }
            }
        } catch (error: SecurityException) {
            // Permission can be revoked after the preflight check.
            logger.warn(LogCategory.LOCATION, "Current location request lost permission", error = error)
            null
        }
        return location
    }

    private suspend fun requestLastKnownLocation(): Location? = try {
        fusedLocationProviderClient.lastLocation.awaitTask()
    } catch (error: SecurityException) {
        logger.warn(LogCategory.LOCATION, "Last known location request lost permission", error = error)
        null
    }

    private fun logLocationResult(location: Location?) {
        logger.info(
            LogCategory.LOCATION,
            "Current location result",
            mapOf(
                "available" to (location != null),
                "hasAccuracy" to (location?.hasAccuracy() == true),
                "accuracyMeters" to location?.takeIf { it.hasAccuracy() }?.accuracy,
            ),
        )
    }

    private companion object {
        const val MAX_LOCATION_AGE_MILLIS = 15_000L
        const val CURRENT_LOCATION_TIMEOUT_MILLIS = 10_000L
    }
}
