@file:Suppress("MaxLineLength")

package com.robertochavez.timetracker.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkHubMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var helper: SupportSQLiteOpenHelper? = null

    @After fun close() {
        helper?.close()
        context.deleteDatabase(DATABASE)
    }

    @Test fun `12 to 13 preserves legacy rows and enforces nullable unique project numbers`() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DATABASE).callback(
                object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE work_locations (id TEXT NOT NULL PRIMARY KEY, label TEXT NOT NULL, address TEXT, latitude REAL NOT NULL, longitude REAL NOT NULL, radiusMeters REAL NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE TABLE work_site_sessions (id TEXT NOT NULL PRIMARY KEY, workLocationId TEXT NOT NULL, workLocationLabelSnapshot TEXT NOT NULL, startEpochMillis INTEGER NOT NULL, endEpochMillis INTEGER)",
                        )
                        db.execSQL("INSERT INTO work_locations VALUES ('legacy','Legacy',NULL,1,2,1609.344,1)")
                        db.execSQL("INSERT INTO work_site_sessions VALUES ('session','legacy','Legacy',1,2)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        val db = helper!!.writableDatabase
        TimeTrackerMigrations.MIGRATION_12_13.migrate(db)
        assertEquals(
            null,
            db.query("SELECT projectNumber FROM work_locations WHERE id='legacy'").use {
                it.moveToFirst()
                it.getString(0)
            },
        )
        assertEquals(
            null,
            db.query("SELECT projectNumberSnapshot FROM work_site_sessions WHERE id='session'").use {
                it.moveToFirst()
                it.getString(0)
            },
        )
        assertTrue(db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='hub_sync_records'").use { it.moveToFirst() })
        db.execSQL(
            "INSERT INTO work_locations (id,label,address,latitude,longitude,radiusMeters,updatedAtEpochMillis,projectNumber) VALUES ('one','One',NULL,1,2,1609.344,2,'TEST-001')",
        )
        runCatching {
            db.execSQL(
                "INSERT INTO work_locations (id,label,address,latitude,longitude,radiusMeters,updatedAtEpochMillis,projectNumber) VALUES ('two','Two',NULL,1,2,1609.344,3,'TEST-001')",
            )
        }
            .onSuccess { error("Unique project number index was not enforced") }
    }

    private companion object {
        const val DATABASE = "work-hub-migration-test.db"
    }
}
