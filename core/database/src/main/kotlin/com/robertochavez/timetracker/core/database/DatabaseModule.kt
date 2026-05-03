package com.robertochavez.timetracker.core.database

import android.content.Context
import androidx.room.Room
import com.robertochavez.timetracker.core.database.dao.ActivityIntervalDao
import com.robertochavez.timetracker.core.database.dao.AwaySessionDao
import com.robertochavez.timetracker.core.database.dao.HomeLocationDao
import com.robertochavez.timetracker.core.database.dao.PayPeriodSettingsDao
import com.robertochavez.timetracker.core.database.dao.WorkLocationDao
import com.robertochavez.timetracker.core.database.dao.WorkPresenceDao
import com.robertochavez.timetracker.core.database.dao.WorkScheduleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TimeTrackerDatabase =
        Room.databaseBuilder(context, TimeTrackerDatabase::class.java, "time_tracker.db")
            .addMigrations(*TimeTrackerMigrations.ALL)
            .build()

    @Provides
    fun provideHomeLocationDao(database: TimeTrackerDatabase): HomeLocationDao = database.homeLocationDao()

    @Provides
    fun provideWorkLocationDao(database: TimeTrackerDatabase): WorkLocationDao = database.workLocationDao()

    @Provides
    fun provideWorkPresenceDao(database: TimeTrackerDatabase): WorkPresenceDao = database.workPresenceDao()

    @Provides
    fun provideAwaySessionDao(database: TimeTrackerDatabase): AwaySessionDao = database.awaySessionDao()

    @Provides
    fun provideActivityIntervalDao(database: TimeTrackerDatabase): ActivityIntervalDao = database.activityIntervalDao()

    @Provides
    fun provideWorkScheduleDao(database: TimeTrackerDatabase): WorkScheduleDao = database.workScheduleDao()

    @Provides
    fun providePayPeriodSettingsDao(database: TimeTrackerDatabase): PayPeriodSettingsDao = database.payPeriodSettingsDao()

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
