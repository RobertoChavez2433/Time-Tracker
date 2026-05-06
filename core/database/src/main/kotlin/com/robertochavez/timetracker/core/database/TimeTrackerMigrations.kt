package com.robertochavez.timetracker.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TimeTrackerMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `work_locations` (
                    `id` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `radiusMeters` REAL NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `work_presence` (
                    `id` TEXT NOT NULL,
                    `atWork` INTEGER NOT NULL,
                    `updatedAtEpochMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `work_locations` ADD COLUMN `label` TEXT NOT NULL DEFAULT 'Work site'")
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `work_site_sessions` (
                    `id` TEXT NOT NULL,
                    `workLocationId` TEXT NOT NULL,
                    `startEpochMillis` INTEGER NOT NULL,
                    `endEpochMillis` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_site_sessions_workLocationId` ON `work_site_sessions` (`workLocationId`)")
            db.execSQL(
                """
                INSERT OR IGNORE INTO `work_site_sessions` (`id`, `workLocationId`, `startEpochMillis`, `endEpochMillis`)
                SELECT 'legacy-site-' || `id`, 'work', `startEpochMillis`, `endEpochMillis`
                FROM `activity_intervals`
                WHERE `bucket` = 'IDLE' AND `endEpochMillis` IS NOT NULL
                """.trimIndent(),
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
