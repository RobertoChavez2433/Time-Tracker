package com.robertochavez.timetracker.core.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.robertochavez.timetracker.core.common.model.HomeLocation
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
) : CurrentHomeLocationProvider {
    @SuppressLint("MissingPermission")
    override suspend fun currentPreciseHomeLocation(radiusMeters: Float): HomeLocation? = if (!context.hasFineLocationPermission()) {
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
        } catch (_: SecurityException) {
            // Permission can be revoked after the preflight check.
            null
        }

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
