package com.robertochavez.timetracker.core.location.mileage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.LocationResult
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
class DriveLocationBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var driveMileageTracker: DriveMileageTracker

    @Inject lateinit var logger: AppLogger

    override fun onReceive(context: Context, intent: Intent) {
        if (!LocationResult.hasResult(intent)) {
            logger.info(LogCategory.LOCATION, "Drive mileage location broadcast had no result")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = LocationResult.extractResult(intent) ?: return@launch
                result.locations.forEach { location ->
                    driveMileageTracker.recordLocation(location)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
