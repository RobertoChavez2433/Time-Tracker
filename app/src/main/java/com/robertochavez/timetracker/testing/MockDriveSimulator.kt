package com.robertochavez.timetracker.testing

import android.location.Location
import android.os.SystemClock
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.location.TrackingRegistrationSynchronizer
import com.robertochavez.timetracker.core.location.mileage.DriveMileageTracker
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockDriveSimulator @Inject constructor(
    private val setupSources: SetupSnapshotSources,
    private val trackingRepository: TrackingRepository,
    private val driveMileageTracker: DriveMileageTracker,
    private val trackingRegistrationSynchronizer: TrackingRegistrationSynchronizer,
    private val snapshotProvider: AppStateSnapshotProvider,
    private val clock: Clock,
    private val logger: AppLogger,
) {
    suspend fun prepare(config: MockDriveConfig, runId: String, actorId: String): Map<String, Any?> {
        val startAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)
        trackingRepository.stopActiveAwaySession(startAt)
        setupSources.workScheduleRepository.setWorkSchedule(WorkSchedule(DayOfWeek.entries.toSet()))
        setupSources.appSettingsRepository.setPrivacyDisclosureAccepted(true)
        setupSources.appSettingsRepository.setActivityDetectionEnabled(true)
        setupSources.homeLocationRepository.setHomeLocation(
            HomeLocation(
                latitude = config.startLatitude,
                longitude = config.startLongitude,
                radiusMeters = config.homeRadiusMeters,
                updatedAt = startAt,
            ),
        )
        trackingRegistrationSynchronizer.synchronize("testing_prepare_mock_drive")
        delay(REGISTRATION_SETTLE_DELAY_MILLIS)
        trackingRepository.stopActiveAwaySession(startAt)
        val session = trackingRepository.startAwaySessionIfTrackable(startAt)
            ?: trackingRepository.startManualSession(startAt)
        trackingRepository.recordActivityTransition(ActivityBucket.DRIVE, startAt)
        driveMileageTracker.clearBaseline()
        driveMileageTracker.startTracking()

        logger.info(
            LogCategory.TESTING,
            "Mock drive prepared",
            mapOf("session" to session.id.take(8), "startAtEpochMillis" to startAt.toEpochMilli()),
        )
        return proof(
            scenario = "mock_drive_prepared",
            runId = runId,
            actorId = actorId,
            extra = mapOf(
                "sessionIdPrefix" to session.id.take(8),
                "startAtEpochMillis" to startAt.toEpochMilli(),
                "startLatitude" to config.startLatitude,
                "startLongitude" to config.startLongitude,
            ),
        )
    }

    suspend fun inject(config: MockDriveConfig, runId: String, actorId: String): Map<String, Any?> {
        prepare(config, runId, actorId)
        val startAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)
        val points = buildDrivePoints(config, startAt)
        points.forEach { driveMileageTracker.recordLocation(it) }
        finish(runId, actorId, Instant.ofEpochMilli(points.last().time))

        logger.info(
            LogCategory.TESTING,
            "Mock drive injected",
            mapOf("pointCount" to points.size, "distanceMeters" to config.distanceMeters),
        )
        return proof(
            scenario = "mock_drive_injected",
            runId = runId,
            actorId = actorId,
            extra = mapOf(
                "pointCount" to points.size,
                "requestedDistanceMeters" to config.distanceMeters,
                "durationSeconds" to config.durationSeconds,
            ),
        )
    }

    suspend fun finish(runId: String, actorId: String): Map<String, Any?> =
        finish(runId, actorId, Instant.now(clock).truncatedTo(ChronoUnit.SECONDS))

    private suspend fun finish(runId: String, actorId: String, finishedAt: Instant): Map<String, Any?> {
        driveMileageTracker.stopTracking()
        trackingRepository.stopActiveAwaySession(finishedAt)
        logger.info(LogCategory.TESTING, "Mock drive finished", mapOf("finishedAtEpochMillis" to finishedAt.toEpochMilli()))
        return proof(
            scenario = "mock_drive_finished",
            runId = runId,
            actorId = actorId,
            extra = mapOf("finishedAtEpochMillis" to finishedAt.toEpochMilli()),
        )
    }

    private suspend fun proof(scenario: String, runId: String, actorId: String, extra: Map<String, Any?>): Map<String, Any?> {
        val snapshot = snapshotProvider.build(runId = runId, actorId = actorId)
        val todayReport = snapshot.reportTotals.getValue("today")
        return mapOf(
            "transportReady" to true,
            "proof" to extra + mapOf(
                "scenario" to scenario,
                "todayDrivenMiles" to todayReport.drivenMiles,
                "todayDriveMinutes" to todayReport.driveMinutes,
                "totalDrivenMiles" to snapshot.totalDrivenMiles,
                "sessionCount" to snapshot.sessionCount,
                "activeSession" to snapshot.activeSession?.toMap(),
                "trackableToday" to snapshot.trackableToday,
            ),
            "snapshot" to snapshot.toMap(),
        )
    }

    private fun buildDrivePoints(config: MockDriveConfig, startAt: Instant): List<Location> {
        val pointCount = config.pointCount.coerceAtLeast(MIN_POINT_COUNT)
        val intervalMillis = config.durationSeconds.coerceAtLeast(pointCount.toLong()).times(1_000L) / (pointCount - 1)
        return (0 until pointCount).map { index ->
            val fraction = index.toDouble() / (pointCount - 1)
            val distanceMeters = config.distanceMeters * fraction
            val latitude = config.startLatitude + distanceMeters / METERS_PER_DEGREE_LATITUDE
            Location(MOCK_PROVIDER).apply {
                this.latitude = latitude
                this.longitude = config.startLongitude
                accuracy = config.accuracyMeters
                time = startAt.toEpochMilli() + intervalMillis * index
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() + index
            }
        }
    }

    private companion object {
        const val MOCK_PROVIDER = "time_tracker_mock_drive"
        const val MIN_POINT_COUNT = 2
        const val METERS_PER_DEGREE_LATITUDE = 111_320.0
        const val REGISTRATION_SETTLE_DELAY_MILLIS = 2_000L
    }
}

data class MockDriveConfig(
    val startLatitude: Double,
    val startLongitude: Double,
    val homeRadiusMeters: Float,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val pointCount: Int,
    val accuracyMeters: Float,
)
