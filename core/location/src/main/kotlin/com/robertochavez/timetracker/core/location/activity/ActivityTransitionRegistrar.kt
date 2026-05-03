package com.robertochavez.timetracker.core.location.activity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.robertochavez.timetracker.core.location.awaitTask
import com.robertochavez.timetracker.core.location.hasActivityRecognitionPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface ActivityTransitionRegistrar {
    suspend fun registerDriveAndIdleTransitions()

    suspend fun unregisterDriveAndIdleTransitions()
}

@Singleton
class PlayServicesActivityTransitionRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityRecognitionClient: ActivityRecognitionClient,
) : ActivityTransitionRegistrar {
    @SuppressLint("MissingPermission")
    override suspend fun registerDriveAndIdleTransitions() {
        if (!context.hasActivityRecognitionPermission()) {
            return
        }
        try {
            activityRecognitionClient
                .requestActivityTransitionUpdates(activityTransitionRequest(), pendingIntent())
                .awaitTask()
        } catch (_: SecurityException) {
            // Permission can be revoked after the preflight check.
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun unregisterDriveAndIdleTransitions() {
        if (!context.hasActivityRecognitionPermission()) {
            return
        }
        try {
            activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent()).awaitTask()
        } catch (_: SecurityException) {
            // Permission can be revoked after the preflight check.
        }
    }

    private fun activityTransitionRequest(): ActivityTransitionRequest {
        val transitions = listOf(
            transition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_EXIT),
            transition(DetectedActivity.STILL, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.STILL, ActivityTransition.ACTIVITY_TRANSITION_EXIT),
        )
        return ActivityTransitionRequest(transitions)
    }

    private fun transition(activityType: Int, transitionType: Int): ActivityTransition = ActivityTransition.Builder()
        .setActivityType(activityType)
        .setActivityTransition(transitionType)
        .build()

    private fun pendingIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_IMMUTABLE
            }
        val intent = Intent(context, ActivityTransitionBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(context, ACTIVITY_PENDING_INTENT_REQUEST_CODE, intent, flags)
    }

    companion object {
        const val ACTIVITY_PENDING_INTENT_REQUEST_CODE = 4200
    }
}
