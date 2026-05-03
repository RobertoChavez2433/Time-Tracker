package com.robertochavez.timetracker.core.location.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.robertochavez.timetracker.core.common.domain.ActivityBucketPolicy
import com.robertochavez.timetracker.core.common.domain.MotionActivity
import com.robertochavez.timetracker.core.common.domain.MotionTransition
import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import com.robertochavez.timetracker.core.common.repository.WorkPresenceRepository
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
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

    @Inject lateinit var workPresenceRepository: WorkPresenceRepository

    @Inject lateinit var clock: Clock

    @Inject lateinit var logger: AppLogger

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) {
            logger.info(LogCategory.ACTIVITY, "Activity transition broadcast had no result")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = ActivityTransitionResult.extractResult(intent) ?: return@launch
                val atWork = workPresenceRepository.getWorkPresence().atWork
                result.transitionEvents.forEach { event ->
                    val bucket = ActivityBucketPolicy.classify(
                        activity = event.activityType.toMotionActivity(),
                        transition = event.transitionType.toMotionTransition(),
                        atWork = atWork,
                    )
                    logger.info(LogCategory.ACTIVITY, "Activity transition received", mapOf("bucket" to bucket.name, "atWork" to atWork))
                    trackingSessionController.recordActivityTransition(bucket, Instant.now(clock))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private fun Int.toMotionActivity(): MotionActivity = when (this) {
    DetectedActivity.IN_VEHICLE -> MotionActivity.IN_VEHICLE
    DetectedActivity.STILL -> MotionActivity.STILL
    else -> MotionActivity.OTHER
}

private fun Int.toMotionTransition(): MotionTransition = when (this) {
    ActivityTransition.ACTIVITY_TRANSITION_ENTER -> MotionTransition.ENTER
    ActivityTransition.ACTIVITY_TRANSITION_EXIT -> MotionTransition.EXIT
    else -> MotionTransition.OTHER
}
