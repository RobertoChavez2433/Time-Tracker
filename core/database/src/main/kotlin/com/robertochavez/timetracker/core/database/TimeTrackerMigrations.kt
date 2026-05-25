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
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `work_site_sessions` ADD COLUMN " +
                    "`workLocationLabelSnapshot` TEXT NOT NULL DEFAULT 'Work site'",
            )
        }
    }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Kept as an explicit no-op so existing 6 -> 7 upgrade paths remain valid.
        }
    }

    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM `work_site_sessions` WHERE `id` LIKE 'legacy-site-span-%'")
        }
    }

    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
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
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`sessionId`) REFERENCES `away_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_drive_movement_observations_sessionId` " +
                    "ON `drive_movement_observations` (`sessionId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_drive_movement_observations_startEpochMillis` " +
                    "ON `drive_movement_observations` (`startEpochMillis`)",
            )
            db.execSQL("DELETE FROM `work_site_sessions` WHERE `id` LIKE 'legacy-site-%'")
        }
    }

    val ALL: Array<Migration> =
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
        )
}
