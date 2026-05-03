package com.robertochavez.timetracker.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.logging.warn
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface CurrentHomeLocationProvider {
    suspend fun currentPreciseHomeLocation(radiusMeters: Float = HomeLocation.MINIMUM_RADIUS_METERS): HomeLocation?
}

@Singleton
class PlayServicesCurrentHomeLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    private val clock: Clock,
    private val logger: AppLogger,
) : CurrentHomeLocationProvider {
    @SuppressLint("MissingPermission")
    override suspend fun currentPreciseHomeLocation(radiusMeters: Float): HomeLocation? = if (!context.hasFineLocationPermission()) {
        logger.info(LogCategory.LOCATION, "Current home location skipped because fine location is missing")
        null
    } else {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MILLIS)
            .build()
        val location = try {
            fusedLocationProviderClient
                .getCurrentLocation(request, CancellationTokenSource().token)
                .awaitTask()
        } catch (error: SecurityException) {
            // Permission can be revoked after the preflight check.
            logger.warn(LogCategory.LOCATION, "Current location request lost permission", error = error)
            null
        }

        logger.info(LogCategory.LOCATION, "Current home location result", mapOf("available" to (location != null)))
        location?.let {
            HomeLocation(
                latitude = it.latitude,
                longitude = it.longitude,
                radiusMeters = radiusMeters,
                updatedAt = Instant.now(clock),
            )
        }
    }

    private companion object {
        const val MAX_LOCATION_AGE_MILLIS = 15_000L
    }
}
