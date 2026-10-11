package com.robertochavez.timetracker.core.database.repository

import androidx.room.withTransaction
import com.robertochavez.timetracker.core.common.domain.ManualCorrectionService
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.ClassificationStateResetter
import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.common.util.METERS_PER_MILE
import com.robertochavez.timetracker.core.common.util.safePrefix
import com.robertochavez.timetracker.core.database.TimeTrackerDatabase
import com.robertochavez.timetracker.core.database.dao.ActivityIntervalDao
import com.robertochavez.timetracker.core.database.dao.AwaySessionDao
import com.robertochavez.timetracker.core.database.dao.WorkScheduleDao
import com.robertochavez.timetracker.core.database.entity.ActivityIntervalEntity
import com.robertochavez.timetracker.core.database.entity.AwaySessionEntity
import com.robertochavez.timetracker.core.database.entity.WorkScheduleEntity
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("TooManyFunctions") // Implements tracking, range queries, and atomic corrections.
@Singleton
class RoomTrackingRepository @Inject constructor(
    private val database: TimeTrackerDatabase,
    private val awaySessionDao: AwaySessionDao,
    private val activityIntervalDao: ActivityIntervalDao,
    private val workScheduleDao: WorkScheduleDao,
    private val clock: Clock,
    private val logger: AppLogger,
    private val classificationStateResetter: ClassificationStateResetter = ClassificationStateResetter {},
) : TrackingRepository,
    TrackingSessionController {
    override fun observeSessions(): Flow<List<AwaySession>> = awaySessionDao.observeSessions()
        .map { sessions -> sessions.map(AwaySessionEntity::toModel) }

    override fun observeSessions(startInclusive: Instant, endExclusive: Instant): Flow<List<AwaySession>> =
        awaySessionDao.observeSessionsInRange(
            startInclusiveEpochMillis = startInclusive.toEpochMilli(),
            endExclusiveEpochMillis = endExclusive.toEpochMilli(),
        ).map { sessions -> sessions.map(AwaySessionEntity::toModel) }

    override fun observeActivityIntervals(): Flow<List<ActivityInterval>> = activityIntervalDao.observeIntervals()
        .map { intervals ->
            val openIntervalEnd = Instant.now(clock)
            intervals.mapNotNull { interval -> interval.toModelOrNull(openIntervalEnd) }
        }

    override fun observeActivityIntervals(startInclusive: Instant, endExclusive: Instant): Flow<List<ActivityInterval>> =
        activityIntervalDao.observeIntervalsInRange(
            startInclusiveEpochMillis = startInclusive.toEpochMilli(),
            endExclusiveEpochMillis = endExclusive.toEpochMilli(),
        ).map { intervals ->
            val openIntervalEnd = Instant.now(clock)
            intervals.mapNotNull { interval -> interval.toModelOrNull(openIntervalEnd) }
        }

    override suspend fun startManualSession(at: Instant?): AwaySession = database.withTransaction {
        createSession(
            awaySessionDao,
            activityIntervalDao,
            logger,
            at ?: Instant.now(clock),
        )
    }

    override suspend fun startAwaySessionIfTrackable(at: Instant): AwaySession? {
        val schedule = WorkScheduleEntity.toSchedule(workScheduleDao.getWorkScheduleDays())
        val localDate = at.atZone(clock.zone).toLocalDate()
        if (!schedule.isTrackable(localDate)) {
            logger.info(LogCategory.TRACKING, "Geofence exit ignored for non-trackable day", mapOf("day" to localDate.dayOfWeek.name))
            return null
        }
        return database.withTransaction {
            awaySessionDao.getActiveSession()?.toModel()
                ?: createSession(awaySessionDao, activityIntervalDao, logger, at, source = "geofence")
        }
    }

    override suspend fun stopActiveAwaySession(at: Instant): AwaySession? = database.withTransaction {
        val active = awaySessionDao.getActiveSession() ?: return@withTransaction null
        if (at.toEpochMilli() < active.startEpochMillis) return@withTransaction null
        val stopped = active.toModel().copy(end = at)
        closeOpenActivityInterval(activityIntervalDao, active.id, at)
        awaySessionDao.upsert(AwaySessionEntity.fromModel(stopped))
        classificationStateResetter.clear()
        logger.info(LogCategory.TRACKING, "Away session stopped", mapOf("session" to active.id.safePrefix()))
        stopped
    }

    override suspend fun recordActivityTransition(
        bucket: ActivityBucket,
        at: Instant,
        expectedSessionId: String?,
        evidenceAt: Instant,
    ): Boolean = database.withTransaction {
        val active = awaySessionDao.getActiveSession() ?: return@withTransaction false
        if (expectedSessionId != null && active.id != expectedSessionId) return@withTransaction false
        writeActivityDecision(activityIntervalDao, active, bucket, at, evidenceAt)
    }

    override suspend fun addDrivenDistanceMeters(distanceMeters: Double): AwaySession? = database.withTransaction {
        val active = awaySessionDao.getActiveSession() ?: return@withTransaction null
        val updated = if (distanceMeters > 0.0) {
            active.toModel().copy(drivenMiles = active.drivenMiles + distanceMeters / METERS_PER_MILE)
                .also { awaySessionDao.upsert(AwaySessionEntity.fromModel(it)) }
        } else {
            active.toModel()
        }
        logger.info(
            LogCategory.TRACKING,
            "Driven distance added to active session",
            mapOf(
                "session" to active.id.safePrefix(),
                "distanceAdded" to (distanceMeters > 0.0),
                "totalMiles" to updated.drivenMiles,
            ),
        )
        updated
    }

    override fun observeActiveSession(): Flow<AwaySession?> = awaySessionDao.observeActiveSession().map { it?.toModel() }

    override suspend fun getSession(sessionId: String): AwaySession? = awaySessionDao.getSession(sessionId)?.toModel()

    override suspend fun saveCorrection(
        sessionId: String,
        start: Instant,
        end: Instant?,
        drivenMiles: Double,
        countsTowardTotals: Boolean,
    ): AwaySession? = database.withTransaction {
        val existing = awaySessionDao.getSession(sessionId)?.toModel() ?: return@withTransaction null
        require(drivenMiles.isFinite() && drivenMiles >= 0) { "Miles must be a finite, non-negative number." }
        require(end != null || existing.isActive) { "A completed session must have an end time." }
        val updated = existing.copy(
            start = start,
            end = end,
            drivenMiles = drivenMiles,
            countsTowardTotals = countsTowardTotals,
            manuallyAdjusted = true,
        )
        awaySessionDao.upsert(AwaySessionEntity.fromModel(updated))
        logger.info(LogCategory.TRACKING, "Session correction saved", mapOf("session" to sessionId.safePrefix()))
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
