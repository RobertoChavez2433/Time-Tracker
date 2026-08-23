package com.robertochavez.timetracker.core.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import com.robertochavez.timetracker.core.database.TimeTrackerDatabase
import org.json.JSONObject
import javax.inject.Inject

class BackupImporter @Inject constructor(
    private val database: TimeTrackerDatabase,
    private val cipher: BackupCipher,
    private val appSettingsRepository: AppSettingsRepository,
) {
    suspend fun import(encrypted: ByteArray, passphrase: CharArray) {
        val envelope = JSONObject(String(cipher.decrypt(encrypted, passphrase)))
        val manifest = envelope.getJSONObject("manifest")
        require(manifest.getInt("schemaVersion") == 1) { "Unsupported backup schema." }
        require(manifest.getInt("databaseVersion") <= TimeTrackerDatabase.VERSION) { "Backup requires a newer app version." }
        val tables = envelope.getJSONObject("tables")
        BackupExporter.BACKUP_TABLES.forEach { require(tables.has(it)) { "Backup is missing $it." } }
        val sqlite = database.openHelper.writableDatabase
        require(destinationIsEmpty(sqlite)) { "Import is allowed only into an empty Time Tracker database." }
        sqlite.beginTransaction()
        try {
            BackupExporter.BACKUP_TABLES.reversed().forEach { sqlite.execSQL("DELETE FROM `$it`") }
            BackupExporter.BACKUP_TABLES.forEach { table ->
                val rows = tables.getJSONArray(table)
                repeat(rows.length()) { insert(sqlite, table, rows.getJSONObject(it)) }
            }
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
        envelope.optJSONObject("appSettings")?.let { settings ->
            appSettingsRepository.setMinimalActiveNotificationEnabled(settings.optBoolean("minimalActiveNotificationEnabled"))
            appSettingsRepository.setLiveTimerNotificationEnabled(settings.optBoolean("liveTimerNotificationEnabled"))
            appSettingsRepository.setPrivacyDisclosureAccepted(settings.optBoolean("privacyDisclosureAccepted"))
            appSettingsRepository.setActivityDetectionEnabled(settings.optBoolean("activityDetectionEnabled"))
            appSettingsRepository.setMapDisclosureAccepted(settings.optBoolean("mapDisclosureAccepted"))
        }
    }

    private fun destinationIsEmpty(db: SupportSQLiteDatabase): Boolean = USER_DATA_TABLES.all { table ->
        db.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst() && it.getLong(0) == 0L }
    }

    private fun insert(db: SupportSQLiteDatabase, table: String, row: JSONObject) {
        val columns = row.keys().asSequence().toList()
        val statement = db.compileStatement(
            "INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})",
        )
        columns.forEachIndexed { index, column -> statement.bind(index + 1, row.opt(column)) }
        statement.executeInsert()
    }

    private fun SupportSQLiteStatement.bind(index: Int, value: Any?) = when (value) {
        null, JSONObject.NULL -> bindNull(index)
        is Int -> bindLong(index, value.toLong())
        is Long -> bindLong(index, value)
        is Number -> bindDouble(index, value.toDouble())
        else -> bindString(index, value.toString())
    }

    private companion object {
        val USER_DATA_TABLES = listOf(
            "home_locations",
            "away_sessions",
            "activity_intervals",
            "work_locations",
            "work_presence",
            "work_site_sessions",
        )
    }
}
