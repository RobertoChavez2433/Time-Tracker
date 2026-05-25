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
import com.robertochavez.timetracker.core.common.domain.DriveMovementSegment
import com.robertochavez.timetracker.core.common.domain.MovementDriveInference
import com.robertochavez.timetracker.core.common.domain.MovementDriveInferenceState
import com.robertochavez.timetracker.core.common.model.DriveMovementObservationDraft
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
    private val clock: Clock,
    private val logger: AppLogger,
) : DriveMileageTracker {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

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
        if (preferences.getBoolean(KEY_ACTIVE, false) && preferences.trackingMode() == mode) return

        preferences.edit().putBoolean(KEY_ACTIVE, true).apply()
        try {
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

    override suspend fun stopTracking() {
        preferences.edit()
            .putBoolean(KEY_ACTIVE, false)
            .remove(KEY_TRACKING_MODE)
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
        val result = DriveMileageCalculator.calculate(preferences.lastPoint(), current)
        val atWork = workPresenceRepository.getWorkPresence().atWork
        val segment = result.movementSegment
        if (segment != null) {
            val session = trackingSessionController.recordDriveMovementObservation(
                DriveMovementObservationDraft(
                    start = segment.start,
                    end = segment.end,
                    distanceMeters = segment.distanceMeters,
                    countedDistanceMeters = result.distanceMeters,
                    speedMilesPerHour = segment.speedMilesPerHour,
                    vehicleLike = segment.isVehicleLike,
                    atWork = atWork,
                ),
            )
            if (session == null) {
                logger.info(LogCategory.LOCATION, "Drive movement observation ignored because no away session is active")
            }
        }
        handleMovementInference(segment, atWork)
        preferences.edit().putLastPoint(result.baseline).apply()
        logger.info(
            LogCategory.LOCATION,
            "Drive mileage location processed",
            mapOf(
                "distanceMeters" to result.distanceMeters,
                "hasBaseline" to (result.baseline != null),
                "accuracyMeters" to current.accuracyMeters,
                "movementMph" to result.movementSegment?.speedMilesPerHour,
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

    private companion object {
        const val PREFERENCES_NAME = "drive_mileage_tracker"
        const val ACTION_DRIVE_LOCATION_UPDATE = "com.robertochavez.timetracker.action.DRIVE_LOCATION_UPDATE"
        const val DRIVE_LOCATION_PENDING_INTENT_REQUEST_CODE = 4300
    }
}

private enum class LocationTrackingMode(
    val priority: Int,
    val updateIntervalMillis: Long,
    val minUpdateIntervalMillis: Long,
    val maxUpdateDelayMillis: Long,
    val minUpdateDistanceMeters: Float,
) {
    BALANCED(
        priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        updateIntervalMillis = 60_000L,
        minUpdateIntervalMillis = 30_000L,
        maxUpdateDelayMillis = 120_000L,
        minUpdateDistanceMeters = 50f,
    ),
    VEHICLE(
        priority = Priority.PRIORITY_HIGH_ACCURACY,
        updateIntervalMillis = 15_000L,
        minUpdateIntervalMillis = 5_000L,
        maxUpdateDelayMillis = 30_000L,
        minUpdateDistanceMeters = 20f,
    ),
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

private fun SharedPreferences.trackingMode(): LocationTrackingMode? =
    getString(KEY_TRACKING_MODE, null)?.let { name -> LocationTrackingMode.entries.firstOrNull { it.name == name } }

private fun SharedPreferences.Editor.putTrackingMode(mode: LocationTrackingMode): SharedPreferences.Editor =
    putString(KEY_TRACKING_MODE, mode.name)

private fun SharedPreferences.inferredDriveCandidateStart(): Instant? =
    getLong(KEY_INFERRED_DRIVE_CANDIDATE_START_EPOCH_MILLIS, Long.MIN_VALUE)
        .takeIf { it != Long.MIN_VALUE }
        ?.let(Instant::ofEpochMilli)

private fun SharedPreferences.Editor.putInferredDriveCandidateStart(start: Instant): SharedPreferences.Editor =
    putLong(KEY_INFERRED_DRIVE_CANDIDATE_START_EPOCH_MILLIS, start.toEpochMilli())

private fun SharedPreferences.inferredDriveActive(): Boolean = getBoolean(KEY_INFERRED_DRIVE_ACTIVE, false)

private fun SharedPreferences.movementInferenceState(): MovementDriveInferenceState = MovementDriveInferenceState(
    candidateStart = inferredDriveCandidateStart(),
    driveActive = inferredDriveActive(),
)

private fun SharedPreferences.Editor.putMovementInferenceState(state: MovementDriveInferenceState): SharedPreferences.Editor {
    val candidateStart = state.candidateStart
    if (candidateStart == null) {
        remove(KEY_INFERRED_DRIVE_CANDIDATE_START_EPOCH_MILLIS)
    } else {
        putInferredDriveCandidateStart(candidateStart)
    }
    putBoolean(KEY_INFERRED_DRIVE_ACTIVE, state.driveActive)
    return this
}

private fun SharedPreferences.Editor.clearMovementInference(): SharedPreferences.Editor =
    putMovementInferenceState(MovementDriveInferenceState())

private const val KEY_ACTIVE = "active"
private const val KEY_TRACKING_MODE = "tracking_mode"
private const val KEY_LAST_LATITUDE = "last_latitude"
private const val KEY_LAST_LONGITUDE = "last_longitude"
private const val KEY_LAST_ACCURACY = "last_accuracy"
private const val KEY_LAST_AT_EPOCH_MILLIS = "last_at_epoch_millis"
private const val KEY_INFERRED_DRIVE_CANDIDATE_START_EPOCH_MILLIS = "inferred_drive_candidate_start_epoch_millis"
private const val KEY_INFERRED_DRIVE_ACTIVE = "inferred_drive_active"
