package com.robertochavez.timetracker.core.common.repository

interface AwaySessionPresenceRepository {
    suspend fun hasActiveAwaySession(): Boolean
}
