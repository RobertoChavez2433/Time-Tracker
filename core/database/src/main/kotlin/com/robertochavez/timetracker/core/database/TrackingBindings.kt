package com.robertochavez.timetracker.core.database

import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import com.robertochavez.timetracker.core.common.repository.AwaySessionPresenceRepository
import com.robertochavez.timetracker.core.common.repository.HomeLocationRepository
import com.robertochavez.timetracker.core.common.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import com.robertochavez.timetracker.core.common.repository.WorkPresenceRepository
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.common.repository.WorkSiteSessionRepository
import com.robertochavez.timetracker.core.database.repository.RoomAwaySessionPresenceRepository
import com.robertochavez.timetracker.core.database.repository.RoomHomeLocationRepository
import com.robertochavez.timetracker.core.database.repository.RoomPayPeriodSettingsRepository
import com.robertochavez.timetracker.core.database.repository.RoomTrackingRepository
import com.robertochavez.timetracker.core.database.repository.RoomWorkLocationRepository
import com.robertochavez.timetracker.core.database.repository.RoomWorkPresenceRepository
import com.robertochavez.timetracker.core.database.repository.RoomWorkScheduleRepository
import com.robertochavez.timetracker.core.database.repository.RoomWorkSiteSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingBindings {
    @Binds
    @Singleton
    abstract fun bindHomeLocationRepository(repository: RoomHomeLocationRepository): HomeLocationRepository

    @Binds
    @Singleton
    abstract fun bindWorkLocationRepository(repository: RoomWorkLocationRepository): WorkLocationRepository

    @Binds
    @Singleton
    abstract fun bindWorkPresenceRepository(repository: RoomWorkPresenceRepository): WorkPresenceRepository

    @Binds
    @Singleton
    abstract fun bindWorkSiteSessionRepository(repository: RoomWorkSiteSessionRepository): WorkSiteSessionRepository

    @Binds
    @Singleton
    abstract fun bindWorkScheduleRepository(repository: RoomWorkScheduleRepository): WorkScheduleRepository

    @Binds
    @Singleton
    abstract fun bindPayPeriodSettingsRepository(repository: RoomPayPeriodSettingsRepository): PayPeriodSettingsRepository

    @Binds
    @Singleton
    abstract fun bindTrackingRepository(repository: RoomTrackingRepository): TrackingRepository

    @Binds
    @Singleton
    abstract fun bindTrackingSessionController(repository: RoomTrackingRepository): TrackingSessionController

    @Binds
    @Singleton
    abstract fun bindAwaySessionPresenceRepository(repository: RoomAwaySessionPresenceRepository): AwaySessionPresenceRepository
}
