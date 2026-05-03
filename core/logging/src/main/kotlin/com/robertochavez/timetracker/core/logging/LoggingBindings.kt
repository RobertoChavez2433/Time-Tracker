package com.robertochavez.timetracker.core.logging

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface LoggingBindings {
    @Binds
    @Singleton
    fun bindAppLogger(logger: TimeTrackerLogger): AppLogger
}
