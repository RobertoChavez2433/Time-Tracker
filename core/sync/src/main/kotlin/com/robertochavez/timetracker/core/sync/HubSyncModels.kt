package com.robertochavez.timetracker.core.sync

import java.time.Instant
import java.time.LocalDate

enum class HubPayloadType { DAILY_SUMMARY, PROJECT_DAY }
enum class HubSyncState { PENDING, TRANSFERRED, ACCEPTED, FAILED }
enum class HubTransportKind { DOCUMENT_TREE, DIRECT_NOTION }

data class HubSyncSettings(
    val activationDate: LocalDate? = null,
    val outboxTreeUri: String? = null,
    val transportKind: HubTransportKind = HubTransportKind.DOCUMENT_TREE,
    val documentProviderVerified: Boolean = false,
    val dailyLogDatabaseId: String = "",
    val projectsDatabaseId: String = "",
    val timeRecordsDatabaseId: String = "",
)

data class HubTransferResult(val transferId: String, val acceptedSourceIds: Set<String>, val rejected: Map<String, String> = emptyMap())

data class HubReceipt(
    val transferId: String,
    val acceptedSourceIds: Set<String>,
    val rejected: Map<String, String>,
    val receivedAt: Instant,
)

data class HubSyncStatus(val pending: Int = 0, val failed: Int = 0, val accepted: Int = 0, val lastMessage: String = "Not configured")
