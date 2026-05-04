package com.robertochavez.timetracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.robertochavez.timetracker.core.database.dao.ActivityIntervalDao
import com.robertochavez.timetracker.core.database.dao.AwaySessionDao
import com.robertochavez.timetracker.core.database.dao.HomeLocationDao
import com.robertochavez.timetracker.core.database.dao.PayPeriodSettingsDao
import com.robertochavez.timetracker.core.database.dao.WorkLocationDao
import com.robertochavez.timetracker.core.database.dao.WorkPresenceDao
import com.robertochavez.timetracker.core.database.dao.WorkScheduleDao
import com.robertochavez.timetracker.core.database.entity.ActivityIntervalEntity
import com.robertochavez.timetracker.core.database.entity.AwaySessionEntity
import com.robertochavez.timetracker.core.database.entity.HomeLocationEntity
import com.robertochavez.timetracker.core.database.entity.PayPeriodSettingsEntity
import com.robertochavez.timetracker.core.database.entity.WorkLocationEntity
import com.robertochavez.timetracker.core.database.entity.WorkPresenceEntity
import com.robertochavez.timetracker.core.database.entity.WorkScheduleEntity

@Database(
    entities = [
        HomeLocationEntity::class,
        AwaySessionEntity::class,
        ActivityIntervalEntity::class,
        WorkScheduleEntity::class,
        PayPeriodSettingsEntity::class,
        WorkLocationEntity::class,
        WorkPresenceEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class TimeTrackerDatabase : RoomDatabase() {
    abstract fun homeLocationDao(): HomeLocationDao

    abstract fun workLocationDao(): WorkLocationDao

    abstract fun workPresenceDao(): WorkPresenceDao

    abstract fun awaySessionDao(): AwaySessionDao

    abstract fun activityIntervalDao(): ActivityIntervalDao

    abstract fun workScheduleDao(): WorkScheduleDao

    abstract fun payPeriodSettingsDao(): PayPeriodSettingsDao
}
