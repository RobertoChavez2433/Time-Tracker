package com.robertochavez.timetracker.core.database.repository

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.util.safePrefix
import com.robertochavez.timetracker.core.database.dao.ActivityIntervalDao
import com.robertochavez.timetracker.core.database.dao.AwaySessionDao
import com.robertochavez.timetracker.core.database.entity.ActivityIntervalEntity
import com.robertochavez.timetracker.core.database.entity.AwaySessionEntity
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import java.time.Instant
import java.util.UUID

internal suspend fun createSession(
    sessions: AwaySessionDao,
    intervals: ActivityIntervalDao,
    logger: AppLogger,
    at: Instant,
    source: String = "manual",
): AwaySession {
    val session = AwaySession(id = UUID.randomUUID().toString(), start = at, end = null)
    sessions.upsert(AwaySessionEntity.fromModel(session))
    intervals.upsert(ActivityIntervalEntity(UUID.randomUUID().toString(), session.id, ActivityBucket.IDLE, at.toEpochMilli(), null))
    logger.info(LogCategory.TRACKING, "Away session started", mapOf("session" to session.id.safePrefix(), "source" to source))
    return session
}

internal suspend fun writeActivityDecision(
    intervals: ActivityIntervalDao,
    session: AwaySessionEntity,
    bucket: ActivityBucket,
    at: Instant,
    evidenceAt: Instant,
): Boolean {
    require(bucket != ActivityBucket.NEEDS_REVIEW) { "Review is a legacy storage value, not a live classification." }
    val open = intervals.getOpenInterval(session.id)
    val invalid = evidenceAt.toEpochMilli() < session.startEpochMillis ||
        at > evidenceAt ||
        (open != null && evidenceAt.toEpochMilli() < open.startEpochMillis)
    return when {
        invalid -> false
        open?.bucket == bucket -> true
        else -> {
            val boundary = maxOf(at.toEpochMilli(), session.startEpochMillis, open?.startEpochMillis ?: Long.MIN_VALUE)
            if (open != null && boundary == open.startEpochMillis) {
                intervals.upsert(open.copy(bucket = bucket))
            } else {
                if (open != null) intervals.upsert(open.copy(endEpochMillis = boundary))
                intervals.upsert(ActivityIntervalEntity(UUID.randomUUID().toString(), session.id, bucket, boundary, null))
            }
            true
        }
    }
}

internal suspend fun closeOpenActivityInterval(intervals: ActivityIntervalDao, sessionId: String, at: Instant) {
    val open = intervals.getOpenInterval(sessionId) ?: return
    if (at.toEpochMilli() > open.startEpochMillis) {
        intervals.upsert(open.copy(endEpochMillis = at.toEpochMilli()))
    } else if (at.toEpochMilli() == open.startEpochMillis) {
        intervals.deleteById(open.id)
    }
}
