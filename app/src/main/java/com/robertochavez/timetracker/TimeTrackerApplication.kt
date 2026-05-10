package com.robertochavez.timetracker

import android.app.Application
import com.robertochavez.timetracker.core.location.TrackingRegistrationSynchronizer
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.notifications.TrackingNotificationCoordinator
import com.robertochavez.timetracker.testing.DebugStateEndpointServer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TimeTrackerApplication : Application() {
    @Inject lateinit var trackingNotificationCoordinator: TrackingNotificationCoordinator

    @Inject lateinit var trackingRegistrationSynchronizer: TrackingRegistrationSynchronizer

    @Inject lateinit var debugStateEndpointServer: DebugStateEndpointServer

    @Inject lateinit var logger: AppLogger

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        logger.info(LogCategory.LIFECYCLE, "Time Tracker application started")
        trackingNotificationCoordinator.ensureChannels()
        applicationScope.launch {
            trackingRegistrationSynchronizer.synchronize("app_start")
        }
        debugStateEndpointServer.start()
    }
}
