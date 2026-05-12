package com.robertochavez.timetracker.core.database.repository

import com.robertochavez.timetracker.core.common.repository.AwaySessionPresenceRepository
import com.robertochavez.timetracker.core.database.dao.AwaySessionDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAwaySessionPresenceRepository @Inject constructor(private val awaySessionDao: AwaySessionDao) : AwaySessionPresenceRepository {
    override suspend fun hasActiveAwaySession(): Boolean = awaySessionDao.getActiveSession() != null
}
