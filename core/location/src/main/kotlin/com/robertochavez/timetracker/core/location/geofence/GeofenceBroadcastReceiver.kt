package com.robertochavez.timetracker.core.location.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import com.robertochavez.timetracker.core.common.repository.WorkPresenceRepository
import com.robertochavez.timetracker.core.common.repository.WorkSiteSessionRepository
import com.robertochavez.timetracker.core.location.mileage.DriveMileageTracker
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.logging.warn
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var trackingSessionController: TrackingSessionController

    @Inject lateinit var workPresenceRepository: WorkPresenceRepository

    @Inject lateinit var workSiteSessionRepository: WorkSiteSessionRepository

    @Inject lateinit var driveMileageTracker: DriveMileageTracker

    @Inject lateinit var clock: Clock

    @Inject lateinit var logger: AppLogger

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            logger.warn(LogCategory.LOCATION, "Geofence event error", mapOf("errorCode" to event.errorCode))
            return
        }

        val now = Instant.now(clock)
        val requestIds = event.triggeringGeofences?.map { it.requestId }?.toSet().orEmpty()
        logger.info(LogCategory.LOCATION, "Geofence transition received", event.debugData(requestIds))

        if (requestIds.isEmpty() || TimeTrackerGeofenceIds.HOME in requestIds) {
            handleHomeTransition(event.geofenceTransition, now)
        }
        val workLocationIds = requestIds.mapNotNull(TimeTrackerGeofenceIds::workLocationId)
        if (workLocationIds.isNotEmpty()) {
            handleWorkTransition(event.geofenceTransition, workLocationIds, now)
        }
    }

    private suspend fun handleHomeTransition(transition: Int, at: Instant) {
        when (transition) {
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                logger.info(LogCategory.LOCATION, "Home geofence exit received")
                workPresenceRepository.setAtWork(atWork = false, updatedAt = at)
                val started = trackingSessionController.startAwaySessionIfTrackable(at)
                if (started != null) {
                    driveMileageTracker.clearBaseline()
                }
            }
            Geofence.GEOFENCE_TRANSITION_ENTER,
            Geofence.GEOFENCE_TRANSITION_DWELL,
            -> {
                logger.info(LogCategory.LOCATION, "Home geofence enter or dwell received")
                workPresenceRepository.setAtWork(atWork = false, updatedAt = at)
                trackingSessionController.stopActiveAwaySession(at)
                driveMileageTracker.stopTracking()
                workSiteSessionRepository.stopActiveWorkSiteSession(at = at)
            }
        }
    }

    private suspend fun handleWorkTransition(transition: Int, workLocationIds: List<String>, at: Instant) {
        when (transition) {
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                logger.info(LogCategory.LOCATION, "Work geofence exit received")
                workLocationIds.forEach { workLocationId ->
                    workSiteSessionRepository.stopActiveWorkSiteSession(workLocationId, at)
                }
                workPresenceRepository.setAtWork(atWork = false, updatedAt = at)
            }
            Geofence.GEOFENCE_TRANSITION_ENTER,
            Geofence.GEOFENCE_TRANSITION_DWELL,
            -> {
                logger.info(LogCategory.LOCATION, "Work geofence enter or dwell received")
                workLocationIds.forEach { workLocationId ->
                    workSiteSessionRepository.startWorkSiteSession(workLocationId, at)
                }
                workPresenceRepository.setAtWork(atWork = true, updatedAt = at)
            }
        }
    }
}

private fun GeofencingEvent.debugData(requestIds: Set<String>): Map<String, Any?> {
    val location = triggeringLocation
    return mapOf(
        "requestIds" to requestIds.sorted(),
        "transition" to geofenceTransition.name(),
        "triggeringLocationAvailable" to (location != null),
        "triggeringLocationHasAccuracy" to (location?.hasAccuracy() == true),
        "triggeringLocationAccuracyMeters" to location?.takeIf { it.hasAccuracy() }?.accuracy,
    )
}

private fun Int.name(): String = when (this) {
    Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
    Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
    Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
    else -> "UNKNOWN_$this"
}
