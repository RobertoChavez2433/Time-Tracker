package com.robertochavez.timetracker.core.location.activity

import com.robertochavez.timetracker.core.common.domain.ActivityClassificationReducer
import com.robertochavez.timetracker.core.common.domain.ClassificationEvent
import com.robertochavez.timetracker.core.common.domain.ClassificationState
import com.robertochavez.timetracker.core.common.domain.DriveMovementSegment
import com.robertochavez.timetracker.core.common.domain.MotionActivity
import com.robertochavez.timetracker.core.common.domain.MotionTransition
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.common.repository.WorkPresenceRepository
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityClassificationCoordinator @Inject constructor(
    private val tracking: TrackingRepository,
    private val workPresence: WorkPresenceRepository,
    private val store: ClassificationStateStore,
    private val logger: AppLogger,
) {
    private val mutex = Mutex()

    suspend fun motion(activity: MotionActivity, transition: MotionTransition, at: Instant) =
        accept(ClassificationEvent.Motion(activity, transition, at))

    suspend fun position(segment: DriveMovementSegment?, stationary: Boolean, at: Instant) =
        accept(ClassificationEvent.Position(segment, stationary, at))

    suspend fun presenceChanged(at: Instant) =
        accept(ClassificationEvent.Presence(atHome = false, atSite = workPresence.getWorkPresence().atWork, at = at))

    suspend fun reset() = mutex.withLock { store.clear() }

    private suspend fun accept(event: ClassificationEvent) = mutex.withLock {
        val session = tracking.observeActiveSession().first()
        if (session == null) {
            store.clear()
            return@withLock null
        }
        if (event.at < session.start) {
            logger.info(
                LogCategory.ACTIVITY,
                "Classification event ignored",
                mapOf(
                    "reason" to "before_session",
                    "eventAtEpochMillis" to event.at.toEpochMilli(),
                ),
            )
            return@withLock null
        }
        val previous = store.read(session) ?: ClassificationState(atSite = workPresence.getWorkPresence().atWork)
        val decision = ActivityClassificationReducer.reduce(previous, event)
        val applied = decision.accepted &&
            tracking.recordActivityTransition(
                bucket = decision.bucket,
                at = maxOf(session.start, decision.at),
                expectedSessionId = session.id,
                evidenceAt = event.at,
            )
        if (applied) store.write(session, decision.state)
        logger.info(
            LogCategory.ACTIVITY,
            "Classification evidence evaluated",
            mapOf(
                "reason" to decision.reason,
                "bucket" to decision.bucket.name,
                "eventAtEpochMillis" to event.at.toEpochMilli(),
                "applied" to applied,
            ),
        )
        decision.takeIf { applied }
    }
}
