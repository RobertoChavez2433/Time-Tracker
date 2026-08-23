package com.robertochavez.timetracker.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hub_sync_records")
data class HubSyncRecordEntity(
    @PrimaryKey val sourceId: String,
    val payloadType: String,
    val deterministicJson: String,
    val contentHash: String,
    val state: String,
    val transferId: String?,
    val attemptCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastAttemptAtEpochMillis: Long?,
    val sanitizedError: String?,
)
