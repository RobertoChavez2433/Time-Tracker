package com.robertochavez.timetracker.core.location

import android.content.Context
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    fun provideGeofencingClient(@ApplicationContext context: Context): GeofencingClient = LocationServices.getGeofencingClient(context)

    @Provides
    fun provideActivityRecognitionClient(@ApplicationContext context: Context): ActivityRecognitionClient =
        ActivityRecognition.getClient(context)
}
