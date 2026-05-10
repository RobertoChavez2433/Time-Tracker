package com.robertochavez.timetracker.core.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TrackingRegistrationReceiver : BroadcastReceiver() {
    @Inject lateinit var trackingRegistrationSynchronizer: TrackingRegistrationSynchronizer

    @Inject lateinit var logger: AppLogger

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                logger.info(LogCategory.LOCATION, "Tracking registration broadcast received", mapOf("action" to action))
                trackingRegistrationSynchronizer.synchronize(action)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
