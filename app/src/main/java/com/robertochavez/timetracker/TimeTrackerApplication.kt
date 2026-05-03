package com.robertochavez.timetracker

import android.app.Application
import com.robertochavez.timetracker.core.notifications.TrackingNotificationCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TimeTrackerApplication : Application() {
    @Inject lateinit var trackingNotificationCoordinator: TrackingNotificationCoordinator

    override fun onCreate() {
        super.onCreate()
        trackingNotificationCoordinator.ensureChannels()
    }
}
