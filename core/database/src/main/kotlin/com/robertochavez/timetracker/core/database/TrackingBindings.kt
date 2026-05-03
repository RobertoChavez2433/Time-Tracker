package com.robertochavez.timetracker.core.database

import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import com.robertochavez.timetracker.core.database.repository.TrackingRepository
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
    abstract fun bindTrackingSessionController(repository: TrackingRepository): TrackingSessionController
}
