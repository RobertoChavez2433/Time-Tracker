package com.robertochavez.timetracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.robertochavez.timetracker.core.database.dao.ActivityIntervalDao
import com.robertochavez.timetracker.core.database.dao.AwaySessionDao
import com.robertochavez.timetracker.core.database.dao.HomeLocationDao
import com.robertochavez.timetracker.core.database.dao.PayPeriodSettingsDao
import com.robertochavez.timetracker.core.database.dao.WorkScheduleDao
import com.robertochavez.timetracker.core.database.entity.ActivityIntervalEntity
import com.robertochavez.timetracker.core.database.entity.AwaySessionEntity
import com.robertochavez.timetracker.core.database.entity.HomeLocationEntity
import com.robertochavez.timetracker.core.database.entity.PayPeriodSettingsEntity
import com.robertochavez.timetracker.core.database.entity.WorkScheduleEntity

@Database(
    entities = [
        HomeLocationEntity::class,
        AwaySessionEntity::class,
        ActivityIntervalEntity::class,
        WorkScheduleEntity::class,
        PayPeriodSettingsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TimeTrackerDatabase : RoomDatabase() {
    abstract fun homeLocationDao(): HomeLocationDao

    abstract fun awaySessionDao(): AwaySessionDao

    abstract fun activityIntervalDao(): ActivityIntervalDao

    abstract fun workScheduleDao(): WorkScheduleDao

    abstract fun payPeriodSettingsDao(): PayPeriodSettingsDao
}
