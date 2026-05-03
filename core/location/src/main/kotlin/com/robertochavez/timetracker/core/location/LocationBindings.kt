package com.robertochavez.timetracker.core.location

import com.robertochavez.timetracker.core.location.activity.ActivityTransitionRegistrar
import com.robertochavez.timetracker.core.location.activity.PlayServicesActivityTransitionRegistrar
import com.robertochavez.timetracker.core.location.geofence.HomeGeofenceRegistrar
import com.robertochavez.timetracker.core.location.geofence.PlayServicesHomeGeofenceRegistrar
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationBindings {
    @Binds
    @Singleton
    abstract fun bindCurrentHomeLocationProvider(provider: PlayServicesCurrentHomeLocationProvider): CurrentHomeLocationProvider

    @Binds
    @Singleton
    abstract fun bindHomeGeofenceRegistrar(registrar: PlayServicesHomeGeofenceRegistrar): HomeGeofenceRegistrar

    @Binds
    @Singleton
    abstract fun bindActivityTransitionRegistrar(registrar: PlayServicesActivityTransitionRegistrar): ActivityTransitionRegistrar
}
