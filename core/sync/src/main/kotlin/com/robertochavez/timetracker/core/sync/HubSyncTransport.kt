package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.database.entity.HubSyncRecordEntity

interface HubSyncTransport {
    suspend fun transfer(records: List<HubSyncRecordEntity>): HubTransferResult
    suspend fun readReceipts(): List<HubReceipt> = emptyList()
}
