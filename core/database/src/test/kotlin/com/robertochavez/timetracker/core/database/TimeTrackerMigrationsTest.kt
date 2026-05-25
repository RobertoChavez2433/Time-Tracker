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
        val database = helper.createDatabase(context.getDatabasePath(TEST_DATABASE).absolutePath, 1)
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
    fun `schema one has eight upgrade migrations`() {
        assertEquals(8, TimeTrackerMigrations.ALL.size)
    }

    @Test
    fun `migrations add evidence tables and clean unsafe legacy site sessions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = helper.createDatabase(context.getDatabasePath(TEST_DATABASE).absolutePath, 1)

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

            database.query("SELECT id, label, latitude, longitude, radiusMeters, updatedAtEpochMillis FROM work_locations").close()
            database.query("SELECT id, atWork, updatedAtEpochMillis FROM work_presence").close()
            database.query(
                "SELECT id, workLocationId, workLocationLabelSnapshot, startEpochMillis, endEpochMillis FROM work_site_sessions",
            ).close()
            database.query(
                """
                SELECT id, sessionId, startEpochMillis, endEpochMillis, distanceMeters, countedDistanceMeters,
                    speedMilesPerHour, vehicleLike, atWork
                FROM drive_movement_observations
                """.trimIndent(),
            ).close()
            database.query("SELECT id FROM work_site_sessions WHERE id LIKE 'legacy-site-%'").use { cursor ->
                assertEquals(0, cursor.count)
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
                ('idle-two', 'session', 'IDLE', 3500, 4500)
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

    private companion object {
        const val TEST_DATABASE = "time-tracker-migration-test"
    }
}
