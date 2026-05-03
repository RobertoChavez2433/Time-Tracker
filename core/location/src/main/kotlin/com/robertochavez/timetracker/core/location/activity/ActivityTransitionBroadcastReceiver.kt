package com.robertochavez.timetracker.core.location.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.robertochavez.timetracker.core.common.model.ActivityBucket
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
class ActivityTransitionBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var trackingSessionController: TrackingSessionController

    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = ActivityTransitionResult.extractResult(intent) ?: return@launch
                result.transitionEvents.forEach { event ->
                    val bucket = when {
                        event.activityType == DetectedActivity.IN_VEHICLE &&
                            event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> ActivityBucket.DRIVE
                        event.activityType == DetectedActivity.STILL &&
                            event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> ActivityBucket.IDLE
                        event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT -> ActivityBucket.UNCLASSIFIED
                        else -> ActivityBucket.UNCLASSIFIED
                    }
                    trackingSessionController.recordActivityTransition(bucket, Instant.now(clock))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
