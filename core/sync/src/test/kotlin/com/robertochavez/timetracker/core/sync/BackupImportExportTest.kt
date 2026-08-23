package com.robertochavez.timetracker.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.robertochavez.timetracker.core.common.model.AppSettings
import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import com.robertochavez.timetracker.core.database.TimeTrackerDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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

    @Test fun `encrypted logical backup restores identity and rejects nonempty destination`() = runBlocking {
        val source = database()
        source.openHelper.writableDatabase.execSQL(
            "INSERT INTO work_locations (id,label,address,latitude,longitude,radiusMeters,updatedAtEpochMillis,projectNumber) " +
                "VALUES ('site-1','Site','Private address',40.1,-75.2,100,1,'TEST-001')",
        )
        val cipher = BackupCipher()
        val encrypted = BackupExporter(source, cipher, fixedClock(), FakeAppSettingsRepository()).export(passphrase)
        val encryptedText = encrypted.toString(Charsets.ISO_8859_1)
        assertFalse(encryptedText.contains("TEST-001"))
        assertFalse(encryptedText.contains("Private address"))

        val destination = database()
        val importer = BackupImporter(destination, cipher, FakeAppSettingsRepository())
        importer.import(encrypted, passphrase)
        val restored = destination.openHelper.readableDatabase.query(
            "SELECT id, projectNumber FROM work_locations WHERE id='site-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0) to cursor.getString(1)
        }
        assertEquals("site-1" to "TEST-001", restored)
        runCatching { importer.import(encrypted, passphrase) }
            .onSuccess { error("Import into a nonempty destination was not rejected") }
        Unit
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
