package com.robertochavez.timetracker.core.common.repository

interface LocalDataResetter {
    suspend fun deleteAllLocalData()
}
