package com.robertochavez.timetracker.core.location.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var trackingSessionController: TrackingSessionController

    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            return
        }

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_EXIT -> trackingSessionController.startAwaySessionIfTrackable(Instant.now(clock))
            Geofence.GEOFENCE_TRANSITION_ENTER,
            Geofence.GEOFENCE_TRANSITION_DWELL,
            -> trackingSessionController.stopActiveAwaySession(Instant.now(clock))
        }
    }
}
