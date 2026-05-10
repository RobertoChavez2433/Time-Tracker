package com.robertochavez.timetracker.core.location

import com.robertochavez.timetracker.core.location.activity.ActivityTransitionRegistrar
import com.robertochavez.timetracker.core.location.activity.PlayServicesActivityTransitionRegistrar
import com.robertochavez.timetracker.core.location.geofence.HomeGeofenceRegistrar
import com.robertochavez.timetracker.core.location.geofence.PlayServicesHomeGeofenceRegistrar
import com.robertochavez.timetracker.core.location.geofence.PlayServicesWorkGeofenceRegistrar
import com.robertochavez.timetracker.core.location.geofence.WorkGeofenceRegistrar
import com.robertochavez.timetracker.core.location.mileage.DriveMileageTracker
import com.robertochavez.timetracker.core.location.mileage.PlayServicesDriveMileageTracker
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
    abstract fun bindCurrentGeofenceLocationProvider(
        provider: PlayServicesCurrentGeofenceLocationProvider,
    ): CurrentGeofenceLocationProvider

    @Binds
    @Singleton
    abstract fun bindHomeGeofenceRegistrar(registrar: PlayServicesHomeGeofenceRegistrar): HomeGeofenceRegistrar

    @Binds
    @Singleton
    abstract fun bindWorkGeofenceRegistrar(registrar: PlayServicesWorkGeofenceRegistrar): WorkGeofenceRegistrar

    @Binds
    @Singleton
    abstract fun bindActivityTransitionRegistrar(registrar: PlayServicesActivityTransitionRegistrar): ActivityTransitionRegistrar

    @Binds
    @Singleton
    abstract fun bindDriveMileageTracker(tracker: PlayServicesDriveMileageTracker): DriveMileageTracker

    @Binds
    @Singleton
    abstract fun bindTrackingRegistrationSynchronizer(
        synchronizer: PlayServicesTrackingRegistrationSynchronizer,
    ): TrackingRegistrationSynchronizer
}
