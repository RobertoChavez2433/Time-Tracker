package com.robertochavez.timetracker.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.robertochavez.timetracker.core.common.model.AppSettings
import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import com.robertochavez.timetracker.core.database.TimeTrackerDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class BackupImportExportTest {
    private val databases = mutableListOf<TimeTrackerDatabase>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val passphrase = "strong passphrase".toCharArray()

    @After fun closeDatabases() {
        databases.forEach(TimeTrackerDatabase::close)
    }

    @Test fun `plain JSON backup restores identity and settings without a password and rejects nonempty destination`() = runBlocking {
        val source = sourceDatabase()
        val cipher = BackupCipher()
        val sourceSettings = FakeAppSettingsRepository()
        sourceSettings.setMinimalActiveNotificationEnabled(true)
        val backup = BackupExporter(source, fixedClock(), sourceSettings).export()
        assertFalse(cipher.isEncrypted(backup))
        val envelope = JSONObject(backup.toString(Charsets.UTF_8))
        assertEquals(1, envelope.getJSONObject("manifest").getInt("schemaVersion"))
        assertEquals(
            "Private address",
            envelope.getJSONObject("tables").getJSONArray("work_locations").getJSONObject(0).getString("address"),
        )

        val destination = database()
        val restoredSettings = FakeAppSettingsRepository()
        val importer = BackupImporter(destination, cipher, restoredSettings)
        importer.import(backup)
        val restored = destination.openHelper.readableDatabase.query(
            "SELECT id, projectNumber FROM work_locations WHERE id='site-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0) to cursor.getString(1)
        }
        assertEquals("site-1" to "TEST-001", restored)
        assertEquals(sourceSettings.settings.first(), restoredSettings.settings.first())
        runCatching { importer.import(backup) }
            .onSuccess { error("Import into a nonempty destination was not rejected") }
        assertEquals(1, siteCount(destination))
        Unit
    }

    @Test fun `older encrypted backup remains restorable with its original password`() = runBlocking {
        val plain = BackupExporter(sourceDatabase(), fixedClock(), FakeAppSettingsRepository()).export()
        val cipher = BackupCipher()
        val encrypted = cipher.encrypt(plain, passphrase)
        val destination = database()
        val importer = BackupImporter(destination, cipher, FakeAppSettingsRepository())
        assertTrue(cipher.isEncrypted(encrypted))
        assertTrue(runCatching { importer.import(encrypted) }.isFailure)
        assertTrue(runCatching { importer.import(encrypted, "wrong password".toCharArray()) }.isFailure)
        assertEquals(0, siteCount(destination))
        importer.import(encrypted, passphrase)
        assertEquals(1, siteCount(destination))
    }

    @Test fun `invalid JSON rows roll back the whole restore and preserve settings`() = runBlocking {
        val sourceSettings = FakeAppSettingsRepository()
        sourceSettings.setMinimalActiveNotificationEnabled(true)
        val envelope = JSONObject(BackupExporter(sourceDatabase(), fixedClock(), sourceSettings).export().toString(Charsets.UTF_8))
        envelope.getJSONObject("tables").getJSONArray("work_locations").put(JSONObject().put("unknownColumn", "invalid"))
        val destination = database()
        val settings = FakeAppSettingsRepository()
        val originalSettings = settings.settings.first()
        assertTrue(
            runCatching {
                BackupImporter(destination, BackupCipher(), settings).import(envelope.toString().toByteArray())
            }.isFailure,
        )
        assertEquals(0, siteCount(destination))
        assertEquals(originalSettings, settings.settings.first())
    }

    @Test fun `plain backup keeps legacy review intervals unchanged`() = runBlocking {
        val source = database()
        source.openHelper.writableDatabase.execSQL("INSERT INTO away_sessions VALUES ('session',1000,61000,1,0,2.5)")
        source.openHelper.writableDatabase.execSQL(
            "INSERT INTO activity_intervals VALUES ('legacy','session','NEEDS_REVIEW',2000,60000)",
        )
        val settings = FakeAppSettingsRepository()
        val backup = BackupExporter(source, fixedClock(), settings).export()
        val destination = database()
        BackupImporter(destination, BackupCipher(), settings).import(backup)
        val row = destination.activityIntervalDao().observeIntervals().first().single()
        assertEquals("NEEDS_REVIEW", row.bucket.name)
        assertEquals(2000L, row.startEpochMillis)
        assertEquals(60000L, row.endEpochMillis)
        val exported = JSONObject(BackupExporter(destination, fixedClock(), settings).export().toString(Charsets.UTF_8))
        val original = JSONObject(backup.toString(Charsets.UTF_8))
        assertEquals(
            original.getJSONObject("tables").getJSONArray("activity_intervals").toString(),
            exported.getJSONObject("tables").getJSONArray("activity_intervals").toString(),
        )
    }

    private fun sourceDatabase() = database().also {
        it.openHelper.writableDatabase.execSQL(
            "INSERT INTO work_locations (id,label,address,latitude,longitude,radiusMeters,updatedAtEpochMillis,projectNumber) " +
                "VALUES ('site-1','Site','Private address',40.1,-75.2,100,1,'TEST-001')",
        )
    }

    private fun siteCount(database: TimeTrackerDatabase) =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM work_locations").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun database() = Room.inMemoryDatabaseBuilder(context, TimeTrackerDatabase::class.java)
        .allowMainThreadQueries().build().also(databases::add)

    private fun fixedClock() = Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC)
}

private class FakeAppSettingsRepository : AppSettingsRepository {
    private val value = MutableStateFlow(AppSettings(false, false, true, true, false))
    override val settings: Flow<AppSettings> = value
    override suspend fun setMinimalActiveNotificationEnabled(enabled: Boolean) {
        value.value =
            value.value.copy(minimalActiveNotificationEnabled = enabled)
    }
    override suspend fun setLiveTimerNotificationEnabled(enabled: Boolean) {
        value.value =
            value.value.copy(liveTimerNotificationEnabled = enabled)
    }
    override suspend fun setPrivacyDisclosureAccepted(accepted: Boolean) {
        value.value =
            value.value.copy(privacyDisclosureAccepted = accepted)
    }
    override suspend fun setActivityDetectionEnabled(enabled: Boolean) {
        value.value = value.value.copy(activityDetectionEnabled = enabled)
    }
    override suspend fun setMapDisclosureAccepted(accepted: Boolean) {
        value.value = value.value.copy(mapDisclosureAccepted = accepted)
    }
    override suspend fun resetSettings() {
        value.value = AppSettings(false, false, false, false, false)
    }
}
