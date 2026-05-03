package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import java.time.Instant

object ManualCorrectionService {
    fun updateSessionWindow(session: AwaySession, start: Instant, end: Instant?): AwaySession =
        session.copy(start = start, end = end, manuallyAdjusted = true)

    fun setCountsTowardTotals(session: AwaySession, countsTowardTotals: Boolean): AwaySession =
        session.copy(countsTowardTotals = countsTowardTotals, manuallyAdjusted = true)

    fun setDrivenMiles(session: AwaySession, drivenMiles: Double): AwaySession =
        session.copy(drivenMiles = drivenMiles, manuallyAdjusted = true)

    fun replaceActivityIntervals(session: AwaySession, intervals: List<ActivityInterval>): Pair<AwaySession, List<ActivityInterval>> {
        val sessionEnd = session.end
        require(sessionEnd != null) { "Activity intervals can only be manually corrected for completed sessions." }
        intervals.forEach { interval ->
            require(interval.sessionId == session.id) { "Activity interval belongs to a different session." }
            require(!interval.start.isBefore(session.start) && !interval.end.isAfter(sessionEnd)) {
                "Activity interval must stay inside the session window."
            }
        }
        return session.copy(manuallyAdjusted = true) to intervals.sortedBy { it.start }
    }
}
