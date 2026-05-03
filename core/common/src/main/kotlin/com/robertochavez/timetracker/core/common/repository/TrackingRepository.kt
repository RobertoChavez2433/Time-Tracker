package com.robertochavez.timetracker.core.common.repository

import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TrackingRepository : TrackingSessionController {
    fun observeSessions(): Flow<List<AwaySession>>

    fun observeActivityIntervals(): Flow<List<ActivityInterval>>

    suspend fun startManualSession(at: Instant? = null): AwaySession

    suspend fun updateSessionWindow(sessionId: String, start: Instant, end: Instant?): AwaySession?

    suspend fun setCountsTowardTotals(sessionId: String, countsTowardTotals: Boolean): AwaySession?

    suspend fun setDrivenMiles(sessionId: String, drivenMiles: Double): AwaySession?

    suspend fun replaceActivityIntervals(sessionId: String, intervals: List<ActivityInterval>): AwaySession?
}
