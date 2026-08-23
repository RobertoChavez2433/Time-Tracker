package com.robertochavez.timetracker.core.sync

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HubSyncSettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: Flow<HubSyncSettings> = state

    fun current(): HubSyncSettings = state.value

    fun setActivationDate(value: LocalDate?) = update { it.copy(activationDate = value) }

    fun setOutboxTree(uri: Uri?) {
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        update { it.copy(outboxTreeUri = uri?.toString(), documentProviderVerified = false) }
    }

    fun markProviderVerified(verified: Boolean) = update { it.copy(documentProviderVerified = verified) }
    fun setTransport(kind: HubTransportKind) = update { it.copy(transportKind = kind) }
    fun setNotionDatabaseIds(dailyLog: String, projects: String, timeRecords: String) = update {
        it.copy(
            dailyLogDatabaseId = dailyLog.trim(),
            projectsDatabaseId = projects.trim(),
            timeRecordsDatabaseId = timeRecords.trim(),
        )
    }

    fun reset() {
        preferences.edit().clear().apply()
        state.value = HubSyncSettings()
    }

    private fun update(transform: (HubSyncSettings) -> HubSyncSettings) {
        val value = transform(state.value)
        preferences.edit()
            .putString(ACTIVATION_DATE, value.activationDate?.toString())
            .putString(OUTBOX_URI, value.outboxTreeUri)
            .putString(TRANSPORT, value.transportKind.name)
            .putBoolean(PROVIDER_VERIFIED, value.documentProviderVerified)
            .putString(DAILY_LOG_ID, value.dailyLogDatabaseId)
            .putString(PROJECTS_ID, value.projectsDatabaseId)
            .putString(TIME_RECORDS_ID, value.timeRecordsDatabaseId)
            .apply()
        state.value = value
    }

    private fun read() = HubSyncSettings(
        activationDate = preferences.getString(ACTIVATION_DATE, null)?.let(LocalDate::parse),
        outboxTreeUri = preferences.getString(OUTBOX_URI, null),
        transportKind = preferences.getString(TRANSPORT, null)?.let(HubTransportKind::valueOf) ?: HubTransportKind.DOCUMENT_TREE,
        documentProviderVerified = preferences.getBoolean(PROVIDER_VERIFIED, false),
        dailyLogDatabaseId = preferences.getString(DAILY_LOG_ID, "").orEmpty(),
        projectsDatabaseId = preferences.getString(PROJECTS_ID, "").orEmpty(),
        timeRecordsDatabaseId = preferences.getString(TIME_RECORDS_ID, "").orEmpty(),
    )

    private companion object {
        const val NAME = "work_hub_sync"
        const val ACTIVATION_DATE = "activation_date"
        const val OUTBOX_URI = "outbox_uri"
        const val TRANSPORT = "transport"
        const val PROVIDER_VERIFIED = "provider_verified"
        const val DAILY_LOG_ID = "daily_log_id"
        const val PROJECTS_ID = "projects_id"
        const val TIME_RECORDS_ID = "time_records_id"
    }
}
