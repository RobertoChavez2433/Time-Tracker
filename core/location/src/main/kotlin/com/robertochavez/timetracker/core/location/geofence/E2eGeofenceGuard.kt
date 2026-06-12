package com.robertochavez.timetracker.core.location.geofence

import android.location.Location
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.robertochavez.timetracker.core.common.repository.HomeLocationRepository
import com.robertochavez.timetracker.core.common.repository.WorkPresenceRepository
import com.robertochavez.timetracker.core.location.BuildConfig
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.warn
import java.time.Duration
import java.time.Instant

internal class E2eGeofenceGuard(
    private val homeLocationRepository: HomeLocationRepository,
    private val workPresenceRepository: WorkPresenceRepository,
    private val logger: AppLogger,
) {
    suspend fun shouldIgnoreHomeEnterOutsideHome(event: GeofencingEvent, requestIds: Set<String>): Boolean {
        var ignored = false
        if (BuildConfig.E2E_DEBUG_ENABLED && TimeTrackerGeofenceIds.HOME in requestIds && event.isHomeEnterOrDwell()) {
            val triggeringLocation = event.triggeringLocation
            val home = homeLocationRepository.getHomeLocation()
            if (triggeringLocation != null && home != null) {
                val distance = FloatArray(1)
                Location.distanceBetween(triggeringLocation.latitude, triggeringLocation.longitude, home.latitude, home.longitude, distance)
                val maxAcceptedDistance = home.radiusMeters + E2E_HOME_EVENT_RADIUS_TOLERANCE_METERS
                if (distance[0] > maxAcceptedDistance) {
                    logger.warn(
                        LogCategory.LOCATION,
                        "Home enter outside saved Home ignored in e2e debug",
                        event.debugData(requestIds) + mapOf(
                            "triggerDistanceMeters" to distance[0],
                            "homeRadiusMeters" to home.radiusMeters,
                            "maxAcceptedDistanceMeters" to maxAcceptedDistance,
                        ),
                    )
                    ignored = true
                }
            }
        }
        return ignored
    }

    suspend fun shouldIgnoreNonMockTransition(event: GeofencingEvent, requestIds: Set<String>, at: Instant): Boolean {
        var ignored = false
        if (BuildConfig.E2E_DEBUG_ENABLED && event.triggeringLocation?.isMockProviderLocation != true) {
            val workLocationIds = requestIds.mapNotNull(TimeTrackerGeofenceIds::workLocationId)
            ignored = if (event.isHomeEnterOrDwell() && TimeTrackerGeofenceIds.HOME in requestIds) {
                logger.warn(LogCategory.LOCATION, "Non-mock Home enter or dwell ignored in e2e debug", event.debugData(requestIds))
                true
            } else if (event.isWorkExit() && workLocationIds.isNotEmpty()) {
                shouldIgnoreRecentWorkExit(event, requestIds, at)
            } else {
                false
            }
        }
        return ignored
    }

    private suspend fun shouldIgnoreRecentWorkExit(event: GeofencingEvent, requestIds: Set<String>, at: Instant): Boolean {
        val workPresence = workPresenceRepository.getWorkPresence()
        val justEnteredWork = workPresence.atWork &&
            Duration.between(workPresence.updatedAt, at).abs() <= E2E_NON_MOCK_WORK_EXIT_GRACE
        if (justEnteredWork) {
            logger.warn(LogCategory.LOCATION, "Immediate non-mock Work exit ignored in e2e debug", event.debugData(requestIds))
        }
        return justEnteredWork
    }

    private companion object {
        val E2E_NON_MOCK_WORK_EXIT_GRACE: Duration = Duration.ofSeconds(45)
        const val E2E_HOME_EVENT_RADIUS_TOLERANCE_METERS: Float = 75f
    }
}

private fun GeofencingEvent.isHomeEnterOrDwell(): Boolean = geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
    geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL

private fun GeofencingEvent.isWorkExit(): Boolean = geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT

internal fun GeofencingEvent.debugData(requestIds: Set<String>): Map<String, Any?> {
    val location = triggeringLocation
    return mapOf(
        "requestIds" to requestIds.sorted(),
        "transition" to geofenceTransition.name(),
        "triggeringLocationProvider" to location?.provider,
        "triggeringLocationAvailable" to (location != null),
        "triggeringLocationHasAccuracy" to (location?.hasAccuracy() == true),
        "triggeringLocationAccuracyMeters" to location?.takeIf { it.hasAccuracy() }?.accuracy,
        "triggeringLocationMock" to location?.isMockProviderLocation,
    )
}

internal val Location.isMockProviderLocation: Boolean
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider

internal fun Int.name(): String = when (this) {
    Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
    Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
    Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
    else -> "UNKNOWN_$this"
}

internal fun String.toGeofenceTransitionOrNull(): Int? = when (uppercase()) {
    "ENTER" -> Geofence.GEOFENCE_TRANSITION_ENTER
    "EXIT" -> Geofence.GEOFENCE_TRANSITION_EXIT
    "DWELL" -> Geofence.GEOFENCE_TRANSITION_DWELL
    else -> null
}
