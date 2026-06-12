package com.robertochavez.timetracker.core.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TimeTrackerMigrationsTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = TimeTrackerDatabase::class.java,
    )

    @Test
    fun `version one schema opens as migration baseline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = helper.createDatabase(databasePath(context, BASELINE_TEST_DATABASE), 1)
        try {
            database.query("SELECT id, latitude, longitude, radiusMeters, updatedAtEpochMillis FROM home_locations").close()
            database.query("SELECT id, startEpochMillis, endEpochMillis, drivenMiles FROM away_sessions").close()
            database.query("SELECT sessionId, bucket, startEpochMillis, endEpochMillis FROM activity_intervals").close()
            database.query("SELECT dayOfWeek, trackable FROM work_schedule_days").close()
            database.query("SELECT id, biweeklyAnchorEpochDay FROM pay_period_settings").close()
        } finally {
            database.close()
        }
    }

    @Test
    fun `schema one has nine upgrade migrations`() {
        assertEquals(9, TimeTrackerMigrations.ALL.size)
    }

    @Test
    fun `migrates legacy bucket data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = helper.createDatabase(databasePath(context, UPGRADE_TEST_DATABASE), 1)

        try {
            TimeTrackerMigrations.MIGRATION_1_2.migrate(database)
            TimeTrackerMigrations.MIGRATION_2_3.migrate(database)
            TimeTrackerMigrations.MIGRATION_3_4.migrate(database)
            TimeTrackerMigrations.MIGRATION_4_5.migrate(database)
            TimeTrackerMigrations.MIGRATION_5_6.migrate(database)
            insertLegacyIdleEvidence(database)
            TimeTrackerMigrations.MIGRATION_6_7.migrate(database)
            insertUnsafeLegacySiteSessions(database)
            TimeTrackerMigrations.MIGRATION_7_8.migrate(database)
            TimeTrackerMigrations.MIGRATION_8_9.migrate(database)
            insertVersionNineMovementHistory(database)
            TimeTrackerMigrations.MIGRATION_9_10.migrate(database)

            database.query("SELECT id, label, latitude, longitude, radiusMeters, updatedAtEpochMillis FROM work_locations").close()
            database.query("SELECT id, atWork, updatedAtEpochMillis FROM work_presence").close()
            database.query(
                "SELECT id, workLocationId, workLocationLabelSnapshot, startEpochMillis, endEpochMillis FROM work_site_sessions",
            ).close()
            database.query("SELECT id FROM work_site_sessions WHERE id LIKE 'legacy-site-%'").use { cursor ->
                assertEquals(0, cursor.count)
            }
            database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'drive_movement_observations'").use { cursor ->
                assertEquals(0, cursor.count)
            }
            database.query("SELECT id FROM activity_intervals WHERE bucket = 'NEEDS_REVIEW'").use { cursor ->
                assertEquals(1, cursor.count)
            }
        } finally {
            database.close()
        }
    }

    private fun insertLegacyIdleEvidence(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO away_sessions (
                id,
                startEpochMillis,
                endEpochMillis,
                countsTowardTotals,
                manuallyAdjusted,
                drivenMiles
            )
            VALUES ('session', 1000, 5000, 1, 0, 10.0)
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO activity_intervals (id, sessionId, bucket, startEpochMillis, endEpochMillis)
            VALUES
                ('idle-one', 'session', 'IDLE', 2000, 2500),
                ('drive-one', 'session', 'DRIVE', 2500, 3500),
                ('review-one', 'session', 'UNCLASSIFIED', 3500, 4500)
            """.trimIndent(),
        )
        database.query("SELECT id FROM work_site_sessions WHERE id LIKE 'legacy-site-%'").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }

    private fun insertUnsafeLegacySiteSessions(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO work_site_sessions (
                id,
                workLocationId,
                workLocationLabelSnapshot,
                startEpochMillis,
                endEpochMillis
            )
            VALUES
                ('legacy-site-idle-one', 'work', 'Work site', 2000, 2500),
                ('legacy-site-span-session-work', 'work', 'Work site', 2000, 4500)
            """.trimIndent(),
        )
    }

    private fun insertVersionNineMovementHistory(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `drive_movement_observations` (
                `id` TEXT NOT NULL,
                `sessionId` TEXT NOT NULL,
                `startEpochMillis` INTEGER NOT NULL,
                `endEpochMillis` INTEGER NOT NULL,
                `distanceMeters` REAL NOT NULL,
                `countedDistanceMeters` REAL NOT NULL,
                `speedMilesPerHour` REAL NOT NULL,
                `vehicleLike` INTEGER NOT NULL,
                `atWork` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }

    private fun databasePath(context: Context, name: String): String {
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        File(path.absolutePath).delete()
        return path.absolutePath
    }

    private companion object {
        const val BASELINE_TEST_DATABASE = "tt-mig-base"
        const val UPGRADE_TEST_DATABASE = "tt-mig-up"
    }
}
