package com.robertochavez.timetracker.core.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
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
    fun `schema one has no migrations yet`() {
        assertEquals(0, TimeTrackerMigrations.ALL.size)
    }

    private companion object {
        const val TEST_DATABASE = "time-tracker-migration-test"
    }
}
