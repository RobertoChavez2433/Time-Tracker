package com.robertochavez.timetracker.core.common.repository

import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface WorkSiteSessionRepository {
    fun observeWorkSiteSessions(): Flow<List<WorkSiteSession>>

    suspend fun startWorkSiteSession(workLocationId: String, at: Instant): WorkSiteSession

    suspend fun stopActiveWorkSiteSession(workLocationId: String? = null, at: Instant): WorkSiteSession?
}
