package com.robertochavez.timetracker.core.datastore

import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatastoreBindings {
    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(repository: SettingsDataStore): AppSettingsRepository
}
