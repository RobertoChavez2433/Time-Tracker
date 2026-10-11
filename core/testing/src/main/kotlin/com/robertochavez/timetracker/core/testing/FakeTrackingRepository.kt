package com.robertochavez.timetracker.core.testing

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.common.util.METERS_PER_MILE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

@Suppress("TooManyFunctions")
class FakeTrackingRepository(initialSessions: List<AwaySession> = emptyList(), initialIntervals: List<ActivityInterval> = emptyList()) :
    TrackingRepository {
    val sessions = MutableStateFlow(initialSessions)
    val intervals = MutableStateFlow(initialIntervals)
    val sessionRangeRequests = mutableListOf<Pair<Instant, Instant>>()
    val intervalRangeRequests = mutableListOf<Pair<Instant, Instant>>()
    val manualStarts = mutableListOf<Instant>()
    val activeStops = mutableListOf<Instant>()
    val activityTransitions = mutableListOf<Pair<ActivityBucket, Instant>>()
    val drivenDistanceUpdates = mutableListOf<Double>()

    override fun observeActiveSession(): Flow<AwaySession?> = sessions.map { values -> values.firstOrNull { it.isActive } }

    override suspend fun getSession(sessionId: String): AwaySession? = sessions.value.firstOrNull { it.id == sessionId }

    override suspend fun saveCorrection(
        sessionId: String,
        start: Instant,
        end: Instant?,
        drivenMiles: Double,
        countsTowardTotals: Boolean,
    ): AwaySession? = updateSession(sessionId) {
        require(drivenMiles.isFinite() && drivenMiles >= 0)
        it.copy(start = start, end = end, drivenMiles = drivenMiles, countsTowardTotals = countsTowardTotals, manuallyAdjusted = true)
    }

    override fun observeSessions(): Flow<List<AwaySession>> = sessions

    override fun observeSessions(startInclusive: Instant, endExclusive: Instant): Flow<List<AwaySession>> {
        sessionRangeRequests += startInclusive to endExclusive
        return sessions.map { awaySessions -> awaySessions.filter { it.overlaps(startInclusive, endExclusive) } }
    }

    override fun observeActivityIntervals(): Flow<List<ActivityInterval>> = intervals

    override fun observeActivityIntervals(startInclusive: Instant, endExclusive: Instant): Flow<List<ActivityInterval>> {
        intervalRangeRequests += startInclusive to endExclusive
        return intervals.map { activityIntervals -> activityIntervals.filter { it.overlaps(startInclusive, endExclusive) } }
    }

    override suspend fun startManualSession(at: Instant?): AwaySession {
        val start = requireNotNull(at) { "FakeTrackingRepository requires explicit start times." }
        manualStarts += start
        val session = AwaySession(id = "manual-${manualStarts.size}", start = start, end = null)
        sessions.value = sessions.value + session
        return session
    }

    override suspend fun startAwaySessionIfTrackable(at: Instant): AwaySession? = startManualSession(at)

    override suspend fun stopActiveAwaySession(at: Instant): AwaySession? {
        activeStops += at
        val active = sessions.value.firstOrNull { it.isActive } ?: return null
        val stopped = active.copy(end = at)
        sessions.value = sessions.value.map { if (it.id == active.id) stopped else it }
        return stopped
    }

    override suspend fun recordActivityTransition(
        bucket: ActivityBucket,
        at: Instant,
        expectedSessionId: String?,
        evidenceAt: Instant,
    ): Boolean {
        activityTransitions += bucket to at
        return true
    }

    override suspend fun addDrivenDistanceMeters(distanceMeters: Double): AwaySession? {
        drivenDistanceUpdates += distanceMeters
        val active = sessions.value.firstOrNull { it.isActive } ?: return null
        val updated = active.copy(drivenMiles = active.drivenMiles + distanceMeters / METERS_PER_MILE)
        sessions.value = sessions.value.map { if (it.id == active.id) updated else it }
        return updated
    }

    override suspend fun updateSessionWindow(sessionId: String, start: Instant, end: Instant?): AwaySession? =
        updateSession(sessionId) { it.copy(start = start, end = end, manuallyAdjusted = true) }

    override suspend fun setCountsTowardTotals(sessionId: String, countsTowardTotals: Boolean): AwaySession? =
        updateSession(sessionId) { it.copy(countsTowardTotals = countsTowardTotals, manuallyAdjusted = true) }

    override suspend fun setDrivenMiles(sessionId: String, drivenMiles: Double): AwaySession? =
        updateSession(sessionId) { it.copy(drivenMiles = drivenMiles, manuallyAdjusted = true) }

    override suspend fun replaceActivityIntervals(sessionId: String, intervals: List<ActivityInterval>): AwaySession? {
        this.intervals.value = this.intervals.value.filterNot { it.sessionId == sessionId } + intervals
        return updateSession(sessionId) { it.copy(manuallyAdjusted = true) }
    }

    private fun updateSession(sessionId: String, transform: (AwaySession) -> AwaySession): AwaySession? {
        var updated: AwaySession? = null
        sessions.value = sessions.value.map {
            if (it.id == sessionId) {
                transform(it).also { session -> updated = session }
            } else {
                it
            }
        }
        return updated
    }
}

private fun AwaySession.overlaps(startInclusive: Instant, endExclusive: Instant): Boolean {
    val effectiveEnd = end
    return start < endExclusive && (effectiveEnd == null || effectiveEnd > startInclusive)
}

private fun ActivityInterval.overlaps(startInclusive: Instant, endExclusive: Instant): Boolean =
    start < endExclusive && end > startInclusive

internal fun WorkSiteSession.overlaps(startInclusive: Instant, endExclusive: Instant): Boolean {
    val effectiveEnd = end
    return start < endExclusive && (effectiveEnd == null || effectiveEnd > startInclusive)
}
