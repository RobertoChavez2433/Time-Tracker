package com.robertochavez.timetracker.core.sync

import android.database.Cursor
import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import com.robertochavez.timetracker.core.database.TimeTrackerDatabase
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class BackupExporter @Inject constructor(
    private val database: TimeTrackerDatabase,
    private val cipher: BackupCipher,
    private val clock: Clock,
    private val appSettingsRepository: AppSettingsRepository,
) {
    suspend fun export(passphrase: CharArray): ByteArray {
        val sqlite = database.openHelper.readableDatabase
        val tables = JSONObject()
        BACKUP_TABLES.forEach { table -> tables.put(table, sqlite.query("SELECT * FROM `$table`").use(::rows)) }
        val settings = appSettingsRepository.settings.first()
        val envelope = JSONObject()
            .put(
                "manifest",
                JSONObject().put(
                    "schemaVersion",
                    1,
                ).put("databaseVersion", TimeTrackerDatabase.VERSION).put("exportedAt", Instant.now(clock)),
            )
            .put("tables", tables)
            .put(
                "appSettings",
                JSONObject()
                    .put("minimalActiveNotificationEnabled", settings.minimalActiveNotificationEnabled)
                    .put("liveTimerNotificationEnabled", settings.liveTimerNotificationEnabled)
                    .put("privacyDisclosureAccepted", settings.privacyDisclosureAccepted)
                    .put("activityDetectionEnabled", settings.activityDetectionEnabled)
                    .put("mapDisclosureAccepted", settings.mapDisclosureAccepted),
            )
        return cipher.encrypt(envelope.toString().toByteArray(), passphrase)
    }

    private fun rows(cursor: Cursor): JSONArray = JSONArray().also { result ->
        while (cursor.moveToNext()) {
            val row = JSONObject()
            cursor.columnNames.forEachIndexed { index, name ->
                row.put(
                    name,
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        Cursor.FIELD_TYPE_BLOB -> error("Unexpected binary data in $name")
                        else -> cursor.getString(index)
                    },
                )
            }
            result.put(row)
        }
    }

    internal companion object {
        val BACKUP_TABLES = listOf(
            "home_locations",
            "away_sessions",
            "activity_intervals",
            "work_schedule_days",
            "pay_period_settings",
            "work_locations",
            "work_presence",
            "work_site_sessions",
        )
    }
}
