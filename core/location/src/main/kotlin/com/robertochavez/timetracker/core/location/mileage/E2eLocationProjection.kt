package com.robertochavez.timetracker.core.location.mileage

import android.location.Location
import android.os.Build
import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import com.robertochavez.timetracker.core.common.repository.HomeLocationRepository
import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import com.robertochavez.timetracker.core.common.repository.WorkPresenceRepository
import com.robertochavez.timetracker.core.common.repository.WorkSiteSessionRepository
import com.robertochavez.timetracker.core.location.BuildConfig
import com.robertochavez.timetracker.core.location.activity.ActivityClassificationCoordinator
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import java.time.Instant
import javax.inject.Inject

class E2eLocationProjection @Inject constructor(
    private val classification: ActivityClassificationCoordinator,
    private val trackingSessionController: TrackingSessionController,
    private val homeLocationRepository: HomeLocationRepository,
    private val workLocationRepository: WorkLocationRepository,
    private val workPresenceRepository: WorkPresenceRepository,
    private val workSiteSessionRepository: WorkSiteSessionRepository,
    private val logger: AppLogger,
) {
    suspend fun applyHomeGeofenceProjection(location: Location, at: Instant): Boolean {
        var projected = false
        if (location.isEligibleE2eMock()) {
            val home = homeLocationRepository.getHomeLocation()
            if (home != null) {
                val accuracyMeters = location.takeIf { it.hasAccuracy() }?.accuracy ?: 0f
                val insideHome = location.distanceMetersTo(home.latitude, home.longitude) <= home.radiusMeters + accuracyMeters
                if (insideHome) {
                    workPresenceRepository.setAtWork(atWork = false, updatedAt = at)
                    trackingSessionController.stopActiveAwaySession(at)
                    workSiteSessionRepository.stopActiveWorkSiteSession(at = at)
                    logger.info(LogCategory.LOCATION, "E2E provider-derived Home geofence enter received")
                    projected = true
                }
            }
        }
        return projected
    }

    suspend fun applyWorkGeofenceProjection(location: Location, at: Instant) {
        if (location.isEligibleE2eMock()) {
            val workLocations = workLocationRepository.getWorkLocations()
            if (workLocations.isNotEmpty()) {
                val currentPresence = workPresenceRepository.getWorkPresence()
                val containingWorkLocation = workLocations.firstOrNull { workLocation ->
                    location.distanceMetersTo(workLocation.latitude, workLocation.longitude) <= workLocation.radiusMeters
                }
                if (containingWorkLocation != null && !currentPresence.atWork) {
                    enterWorkProjection(containingWorkLocation.id, at)
                } else if (currentPresence.atWork && location.isOutsideAll(workLocations)) {
                    exitWorkProjection(at)
                }
            }
        }
    }

    private suspend fun enterWorkProjection(workLocationId: String, at: Instant) {
        workSiteSessionRepository.startWorkSiteSession(workLocationId, at)
        workPresenceRepository.setAtWork(atWork = true, updatedAt = at)
        classification.presenceChanged(at)
        logger.info(
            LogCategory.LOCATION,
            "E2E provider-derived Work geofence enter received",
            mapOf("workLocationId" to workLocationId),
        )
    }

    private suspend fun exitWorkProjection(at: Instant) {
        workSiteSessionRepository.stopActiveWorkSiteSession(at = at)
        workPresenceRepository.setAtWork(atWork = false, updatedAt = at)
        classification.presenceChanged(at)
        logger.info(LogCategory.LOCATION, "E2E provider-derived Work geofence exit received")
    }

    private fun Location.isOutsideAll(workLocations: List<com.robertochavez.timetracker.core.common.model.WorkLocation>): Boolean =
        workLocations.all { workLocation ->
            distanceMetersTo(workLocation.latitude, workLocation.longitude) >
                workLocation.radiusMeters + (takeIf { it.hasAccuracy() }?.accuracy ?: 0f)
        }

    private fun Location.isEligibleE2eMock(): Boolean =
        BuildConfig.E2E_DEBUG_ENABLED && isMockProviderLocation && (!hasAccuracy() || accuracy <= E2E_MAX_ACCURACY_METERS)

    private fun Location.distanceMetersTo(latitude: Double, longitude: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(this.latitude, this.longitude, latitude, longitude, results)
        return results[0]
    }

    private val Location.isMockProviderLocation: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider

    private companion object {
        const val E2E_MAX_ACCURACY_METERS = 100f
    }
}
