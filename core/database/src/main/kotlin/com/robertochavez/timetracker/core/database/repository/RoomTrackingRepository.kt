package com.robertochavez.timetracker.core.database.repository

import androidx.room.withTransaction
import com.robertochavez.timetracker.core.common.domain.ManualCorrectionService
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.DriveMovementObservation
import com.robertochavez.timetracker.core.common.model.DriveMovementObservationDraft
import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.common.util.METERS_PER_MILE
import com.robertochavez.timetracker.core.common.util.safePrefix
import com.robertochavez.timetracker.core.database.TimeTrackerDatabase
import com.robertochavez.timetracker.core.database.dao.ActivityIntervalDao
import com.robertochavez.timetracker.core.database.dao.AwaySessionDao
import com.robertochavez.timetracker.core.database.dao.DriveMovementObservationDao
import com.robertochavez.timetracker.core.database.dao.WorkScheduleDao
import com.robertochavez.timetracker.core.database.entity.ActivityIntervalEntity
import com.robertochavez.timetracker.core.database.entity.AwaySessionEntity
import com.robertochavez.timetracker.core.database.entity.DriveMovementObservationEntity
import com.robertochavez.timetracker.core.database.entity.WorkScheduleEntity
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTrackingRepository @Inject constructor(
    private val database: TimeTrackerDatabase,
    private val awaySessionDao: AwaySessionDao,
    private val activityIntervalDao: ActivityIntervalDao,
    private val driveMovementObservationDao: DriveMovementObservationDao,
    private val workScheduleDao: WorkScheduleDao,
    private val clock: Clock,
    private val logger: AppLogger,
) : TrackingRepository,
    TrackingSessionController {
    override fun observeSessions(): Flow<List<AwaySession>> = awaySessionDao.observeSessions()
        .map { sessions -> sessions.map(AwaySessionEntity::toModel) }

    override fun observeActivityIntervals(): Flow<List<ActivityInterval>> = activityIntervalDao.observeIntervals()
        .map { intervals ->
            val openIntervalEnd = Instant.now(clock)
            intervals.mapNotNull { interval -> interval.toModelOrNull(openIntervalEnd) }
        }

    override fun observeDriveMovementObservations(): Flow<List<DriveMovementObservation>> =
        driveMovementObservationDao.observeObservations()
            .map { observations -> observations.map(DriveMovementObservationEntity::toModel) }

    override suspend fun startManualSession(at: Instant?): AwaySession = createSession(awaySessionDao, logger, at ?: Instant.now(clock))

    override suspend fun startAwaySessionIfTrackable(at: Instant): AwaySession? {
        val schedule = WorkScheduleEntity.toSchedule(workScheduleDao.getWorkScheduleDays())
        val localDate = at.atZone(clock.zone).toLocalDate()
        if (!schedule.isTrackable(localDate)) {
            logger.info(LogCategory.TRACKING, "Geofence exit ignored for non-trackable day", mapOf("day" to localDate.dayOfWeek.name))
            return null
        }
        return database.withTransaction {
            awaySessionDao.getActiveSession()?.toModel() ?: createSession(awaySessionDao, logger, at, source = "geofence")
        }
    }

    override suspend fun stopActiveAwaySession(at: Instant): AwaySession? = database.withTransaction {
        val active = awaySessionDao.getActiveSession() ?: return@withTransaction null
        val stopped = active.toModel().copy(end = at)
        closeOpenActivityInterval(activityIntervalDao, active.id, at)
        awaySessionDao.upsert(AwaySessionEntity.fromModel(stopped))
        logger.info(LogCategory.TRACKING, "Away session stopped", mapOf("session" to active.id.safePrefix()))
        stopped
    }

    override suspend fun recordActivityTransition(bucket: ActivityBucket, at: Instant) {
        database.withTransaction {
            val active = awaySessionDao.getActiveSession() ?: return@withTransaction
            closeOpenActivityInterval(activityIntervalDao, active.id, at)
            activityIntervalDao.upsert(
                ActivityIntervalEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = active.id,
                    bucket = bucket,
                    startEpochMillis = at.toEpochMilli(),
                    endEpochMillis = null,
                ),
            )
            logger.info(
                LogCategory.ACTIVITY,
                "Activity bucket transition recorded",
                mapOf("session" to active.id.safePrefix(), "bucket" to bucket.name),
            )
        }
    }

    override suspend fun recordDriveMovementObservation(observation: DriveMovementObservationDraft): AwaySession? =
        database.withTransaction {
            val active = awaySessionDao.getActiveSession() ?: return@withTransaction null
            driveMovementObservationDao.upsert(
                DriveMovementObservationEntity.fromModel(
                    DriveMovementObservation(
                        id = UUID.randomUUID().toString(),
                        sessionId = active.id,
                        start = observation.start,
                        end = observation.end,
                        distanceMeters = observation.distanceMeters,
                        countedDistanceMeters = observation.countedDistanceMeters,
                        speedMilesPerHour = observation.speedMilesPerHour,
                        vehicleLike = observation.vehicleLike,
                        atWork = observation.atWork,
                    ),
                ),
            )
            val updated = if (observation.countedDistanceMeters > 0.0) {
                active.toModel().copy(drivenMiles = active.drivenMiles + observation.countedDistanceMeters / METERS_PER_MILE)
                    .also { awaySessionDao.upsert(AwaySessionEntity.fromModel(it)) }
            } else {
                active.toModel()
            }
            logger.info(
                LogCategory.TRACKING,
                "Drive movement observation recorded",
                mapOf(
                    "session" to active.id.safePrefix(),
                    "distanceMeters" to observation.distanceMeters,
                    "countedDistanceMeters" to observation.countedDistanceMeters,
                    "totalMiles" to updated.drivenMiles,
                    "startEpochMillis" to observation.start.toEpochMilli(),
                    "endEpochMillis" to observation.end.toEpochMilli(),
                    "vehicleLike" to observation.vehicleLike,
                    "atWork" to observation.atWork,
                ),
            )
            updated
        }

    override suspend fun updateSessionWindow(sessionId: String, start: Instant, end: Instant?): AwaySession? {
        val existing = awaySessionDao.getSession(sessionId)?.toModel() ?: return null
        val updated = ManualCorrectionService.updateSessionWindow(existing, start, end)
        awaySessionDao.upsert(AwaySessionEntity.fromModel(updated))
        logger.info(LogCategory.TRACKING, "Session window manually adjusted", mapOf("session" to sessionId.safePrefix()))
        return updated
    }

    override suspend fun setCountsTowardTotals(sessionId: String, countsTowardTotals: Boolean): AwaySession? {
        val existing = awaySessionDao.getSession(sessionId)?.toModel() ?: return null
        val updated = ManualCorrectionService.setCountsTowardTotals(existing, countsTowardTotals)
        awaySessionDao.upsert(AwaySessionEntity.fromModel(updated))
        logger.info(
            LogCategory.TRACKING,
            "Session totals inclusion changed",
            mapOf("session" to sessionId.safePrefix(), "countsTowardTotals" to countsTowardTotals),
        )
        return updated
    }

    override suspend fun setDrivenMiles(sessionId: String, drivenMiles: Double): AwaySession? {
        val existing = awaySessionDao.getSession(sessionId)?.toModel() ?: return null
        val updated = ManualCorrectionService.setDrivenMiles(existing, drivenMiles)
        awaySessionDao.upsert(AwaySessionEntity.fromModel(updated))
        logger.info(
            LogCategory.TRACKING,
            "Session miles manually adjusted",
            mapOf(
                "session" to sessionId.safePrefix(),
                "miles" to drivenMiles,
            ),
        )
        return updated
    }

    override suspend fun replaceActivityIntervals(sessionId: String, intervals: List<ActivityInterval>): AwaySession? =
        database.withTransaction {
            val existing = awaySessionDao.getSession(sessionId)?.toModel() ?: return@withTransaction null
            val (updated, correctedIntervals) = ManualCorrectionService.replaceActivityIntervals(existing, intervals)
            awaySessionDao.upsert(AwaySessionEntity.fromModel(updated))
            activityIntervalDao.deleteForSession(sessionId)
            activityIntervalDao.upsertAll(correctedIntervals.map(ActivityIntervalEntity::fromModel))
            logger.info(
                LogCategory.ACTIVITY,
                "Session activity intervals replaced",
                mapOf("session" to sessionId.safePrefix(), "intervalCount" to correctedIntervals.size),
            )
            updated
        }
}

private suspend fun createSession(awaySessionDao: AwaySessionDao, logger: AppLogger, at: Instant, source: String = "manual"): AwaySession {
    val session = AwaySession(
        id = UUID.randomUUID().toString(),
        start = at,
        end = null,
    )
    awaySessionDao.upsert(AwaySessionEntity.fromModel(session))
    logger.info(LogCategory.TRACKING, "Away session started", mapOf("session" to session.id.safePrefix(), "source" to source))
    return session
}

private suspend fun closeOpenActivityInterval(activityIntervalDao: ActivityIntervalDao, sessionId: String, at: Instant) {
    val openInterval = activityIntervalDao.getOpenInterval(sessionId) ?: return
    if (at.toEpochMilli() > openInterval.startEpochMillis) {
        activityIntervalDao.upsert(openInterval.copy(endEpochMillis = at.toEpochMilli()))
    } else {
        activityIntervalDao.deleteById(openInterval.id)
    }
}
