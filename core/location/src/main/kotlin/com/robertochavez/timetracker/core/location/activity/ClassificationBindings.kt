package com.robertochavez.timetracker.core.location.activity

import com.robertochavez.timetracker.core.common.model.ClassificationStateResetter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ClassificationBindings {
    @Binds abstract fun stateResetter(store: ClassificationStateStore): ClassificationStateResetter
}
