package com.robertochavez.timetracker.data

import com.robertochavez.timetracker.core.common.repository.LocalDataResetter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface AppDataModule {
    @Binds
    fun bindLocalDataResetter(resetDataRepository: AppLocalDataResetter): LocalDataResetter
}
