package com.robertochavez.timetracker.core.location.mileage

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.robertochavez.timetracker.core.common.domain.DriveMileageCalculator
import com.robertochavez.timetracker.core.common.domain.DriveMileagePoint
import com.robertochavez.timetracker.core.common.domain.DriveMovementSegment
import com.robertochavez.timetracker.core.common.domain.MovementDriveInference
import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import com.robertochavez.timetracker.core.common.repository.WorkPresenceRepository
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
    suspend fun startAwayTracking()

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
    private val workPresenceRepository: WorkPresenceRepository,
    private val e2eLocationProjection: E2eLocationProjection,
    private val clock: Clock,
    private val logger: AppLogger,
) : DriveMileageTracker {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @SuppressLint("MissingPermission")
    override suspend fun startAwayTracking() {
        startLocationUpdates(LocationTrackingMode.BALANCED)
    }

    @SuppressLint("MissingPermission")
    override suspend fun startTracking() {
        startLocationUpdates(LocationTrackingMode.VEHICLE)
    }

    @SuppressLint("MissingPermission")
    private suspend fun startLocationUpdates(mode: LocationTrackingMode) {
        if (!context.hasFineLocationPermission() || !context.hasBackgroundLocationPermission()) {
            logger.info(LogCategory.LOCATION, "Away movement tracking skipped because location permission is missing")
            return
        }
        val wasActive = preferences.getBoolean(KEY_ACTIVE, false)
        if (wasActive && preferences.trackingMode() == mode) return

        preferences.edit()
            .putBoolean(KEY_ACTIVE, true)
            .apply {
                if (!wasActive) {
                    clearLastPoint()
                    clearMovementInference()
                }
            }
            .apply()
        try {
            if (!wasActive) {
                seedBaselineFromLastKnownLocation()
            }
            fusedLocationProviderClient.removeLocationUpdates(pendingIntent()).awaitTask()
            fusedLocationProviderClient
                .requestLocationUpdates(locationRequest(mode), pendingIntent())
                .awaitTask()
            preferences.edit().putTrackingMode(mode).apply()
            logger.info(LogCategory.LOCATION, "Away movement location updates started", mapOf("mode" to mode.name))
        } catch (error: SecurityException) {
            preferences.edit().putBoolean(KEY_ACTIVE, false).apply()
            logger.warn(LogCategory.LOCATION, "Away movement tracking lost location permission", error = error)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun seedBaselineFromLastKnownLocation() {
        runCatching {
            fusedLocationProviderClient.lastLocation.awaitTask()
        }.onSuccess { location ->
            if (location?.isUsableBaseline() == true) {
                preferences.edit().putLastPoint(location.toMileagePoint()).apply()
                logger.info(LogCategory.LOCATION, "Drive mileage baseline seeded from last known location")
            }
        }.onFailure { error ->
            logger.warn(LogCategory.LOCATION, "Drive mileage baseline seed failed", error = error)
        }
    }

    override suspend fun stopTracking() {
        preferences.edit()
            .putBoolean(KEY_ACTIVE, false)
            .clearTrackingMode()
            .clearLastPoint()
            .clearMovementInference()
            .apply()
        try {
            fusedLocationProviderClient.removeLocationUpdates(pendingIntent()).awaitTask()
            logger.info(LogCategory.LOCATION, "Away movement location updates stopped")
        } catch (error: SecurityException) {
            logger.warn(LogCategory.LOCATION, "Away movement stop lost location permission", error = error)
        }
    }

    override suspend fun clearBaseline() {
        preferences.edit().clearLastPoint().clearMovementInference().apply()
        logger.info(LogCategory.LOCATION, "Away movement baseline cleared")
    }

    override suspend fun recordLocation(location: Location) {
        if (!preferences.getBoolean(KEY_ACTIVE, false)) return

        val current = location.toMileagePoint()
        if (e2eLocationProjection.applyHomeGeofenceProjection(location, current.at)) {
            stopTracking()
            return
        }
        e2eLocationProjection.applyWorkGeofenceProjection(location, current.at)
        val result = DriveMileageCalculator.calculate(preferences.lastPoint(), current)
        val atWork = workPresenceRepository.getWorkPresence().atWork
        val segment = result.movementSegment
        if (result.distanceMeters > 0.0) {
            val session = trackingSessionController.addDrivenDistanceMeters(result.distanceMeters)
            if (session == null) {
                logger.info(LogCategory.LOCATION, "Driven distance ignored because no away session is active")
            }
        }
        handleMovementInference(segment, atWork)
        preferences.edit().putLastPoint(result.baseline).apply()
        logger.info(
            LogCategory.LOCATION,
            "Drive mileage location processed",
            mapOf(
                "distanceCounted" to (result.distanceMeters > 0.0),
                "hasBaseline" to (result.baseline != null),
                "accuracyMeters" to current.accuracyMeters,
                "vehicleLikeMovement" to result.movementSegment?.isVehicleLike,
                "atWork" to atWork,
            ),
        )
    }

    private suspend fun handleMovementInference(segment: DriveMovementSegment?, atWork: Boolean) {
        if (segment == null) return
        val result = MovementDriveInference.evaluate(
            state = preferences.movementInferenceState(),
            segment = segment,
            atWork = atWork,
        )
        result.transition?.let { transition ->
            trackingSessionController.recordActivityTransition(transition.bucket, transition.at)
        }
        preferences.edit().putMovementInferenceState(result.nextState).apply()
        if (result.shouldEscalateToVehicle) {
            startTracking()
        }
        if (result.shouldDeEscalateToBalanced) {
            if (preferences.trackingMode() == LocationTrackingMode.VEHICLE) startAwayTracking()
        }
    }

    private fun locationRequest(mode: LocationTrackingMode): LocationRequest =
        LocationRequest.Builder(mode.priority, mode.updateIntervalMillis)
            .setMinUpdateIntervalMillis(mode.minUpdateIntervalMillis)
            .setMinUpdateDistanceMeters(mode.minUpdateDistanceMeters)
            .setMaxUpdateDelayMillis(mode.maxUpdateDelayMillis)
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

    private fun Location.isUsableBaseline(): Boolean = isUsableDriveMileageBaseline(
        timeEpochMillis = time,
        hasAccuracy = hasAccuracy(),
        accuracyMeters = accuracy,
        clockMillis = clock.millis(),
    )

    private companion object {
        const val PREFERENCES_NAME = "drive_mileage_tracker"
        const val ACTION_DRIVE_LOCATION_UPDATE = "com.robertochavez.timetracker.action.DRIVE_LOCATION_UPDATE"
        const val DRIVE_LOCATION_PENDING_INTENT_REQUEST_CODE = 4300
    }
}

internal fun isUsableDriveMileageBaseline(timeEpochMillis: Long, hasAccuracy: Boolean, accuracyMeters: Float, clockMillis: Long): Boolean {
    val accuracyUsable = !hasAccuracy || accuracyMeters <= MAX_BASELINE_ACCURACY_METERS
    val ageMillis = clockMillis - timeEpochMillis
    return accuracyUsable && timeEpochMillis > 0L && ageMillis in 0..MAX_BASELINE_AGE_MILLIS
}

private const val KEY_ACTIVE = "active"
private const val MAX_BASELINE_ACCURACY_METERS = 100f
private const val MAX_BASELINE_AGE_MILLIS = 10 * 60 * 1_000L
