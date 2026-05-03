package com.robertochavez.timetracker

import android.app.Application
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.notifications.TrackingNotificationCoordinator
import com.robertochavez.timetracker.testing.DebugStateEndpointServer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TimeTrackerApplication : Application() {
    @Inject lateinit var trackingNotificationCoordinator: TrackingNotificationCoordinator

    @Inject lateinit var debugStateEndpointServer: DebugStateEndpointServer

    @Inject lateinit var logger: AppLogger

    override fun onCreate() {
        super.onCreate()
        logger.info(LogCategory.LIFECYCLE, "Time Tracker application started")
        trackingNotificationCoordinator.ensureChannels()
        debugStateEndpointServer.start()
    }
}
