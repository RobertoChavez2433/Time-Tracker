package com.robertochavez.timetracker.core.location.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.robertochavez.timetracker.core.common.domain.DriveClassificationPolicy
import com.robertochavez.timetracker.core.common.domain.GeofencePresence
import com.robertochavez.timetracker.core.common.domain.MotionActivity
import com.robertochavez.timetracker.core.common.domain.MotionTransition
import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import com.robertochavez.timetracker.core.common.repository.AwaySessionPresenceRepository
import com.robertochavez.timetracker.core.common.repository.WorkPresenceRepository
import com.robertochavez.timetracker.core.location.BuildConfig
import com.robertochavez.timetracker.core.location.mileage.DriveMileageTracker
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class ActivityTransitionBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var trackingSessionController: TrackingSessionController

    @Inject lateinit var awaySessionPresenceRepository: AwaySessionPresenceRepository

    @Inject lateinit var workPresenceRepository: WorkPresenceRepository

    @Inject lateinit var driveMileageTracker: DriveMileageTracker

    @Inject lateinit var clock: Clock

    @Inject lateinit var logger: AppLogger

    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.E2E_DEBUG_ENABLED && intent.action == ACTION_E2E_ACTIVITY_TRANSITION) {
            receiveE2eTransition(intent)
            return
        }

        if (!ActivityTransitionResult.hasResult(intent)) {
            logger.info(LogCategory.ACTIVITY, "Activity transition broadcast had no result")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = ActivityTransitionResult.extractResult(intent) ?: return@launch
                result.transitionEvents.forEach { event ->
                    handleTransition(
                        activity = event.activityType.toMotionActivity(),
                        transition = event.transitionType.toMotionTransition(),
                        eventAt = event.toInstant(clock),
                        source = "play_services",
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun receiveE2eTransition(intent: Intent) {
        val activity = intent.getStringExtra(EXTRA_E2E_ACTIVITY)?.toMotionActivityOrNull()
        val transition = intent.getStringExtra(EXTRA_E2E_TRANSITION)?.toMotionTransitionOrNull()
        if (activity == null || transition == null) {
            logger.info(LogCategory.ACTIVITY, "E2E activity transition ignored because extras are invalid")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleTransition(
                    activity = activity,
                    transition = transition,
                    eventAt = Instant.now(clock),
                    source = "e2e_broadcast",
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleTransition(activity: MotionActivity, transition: MotionTransition, eventAt: Instant, source: String) {
        val workPresence = DriveClassificationPolicy.workPresenceFrom(
            presence = workPresenceRepository.getWorkPresence(),
            at = eventAt,
        )
        val homePresence = if (awaySessionPresenceRepository.hasActiveAwaySession()) {
            GeofencePresence.OUTSIDE
        } else {
            GeofencePresence.UNKNOWN
        }
        val bucket = DriveClassificationPolicy.classify(
            activity = activity,
            transition = transition,
            homePresence = homePresence,
            workPresence = workPresence,
        )
        logger.info(
            LogCategory.ACTIVITY,
            "Activity transition received",
            mapOf(
                "bucket" to bucket.name,
                "homePresence" to homePresence.name,
                "workPresence" to workPresence.name,
                "eventAtEpochMillis" to eventAt.toEpochMilli(),
                "source" to source,
            ),
        )
        trackingSessionController.recordActivityTransition(bucket, eventAt)
        handleMileageTracking(activity, transition, homePresence == GeofencePresence.OUTSIDE)
    }

    private suspend fun handleMileageTracking(activity: MotionActivity, transition: MotionTransition, awayFromHome: Boolean) {
        if (!awayFromHome) {
            driveMileageTracker.stopTracking()
            return
        }
        when {
            activity == MotionActivity.IN_VEHICLE && transition == MotionTransition.ENTER -> driveMileageTracker.startTracking()
            activity == MotionActivity.IN_VEHICLE && transition == MotionTransition.EXIT -> driveMileageTracker.startAwayTracking()
            activity == MotionActivity.STILL && transition == MotionTransition.ENTER -> driveMileageTracker.startAwayTracking()
        }
    }

    companion object {
        const val ACTION_E2E_ACTIVITY_TRANSITION = "com.robertochavez.timetracker.debug.E2E_ACTIVITY_TRANSITION"
        const val EXTRA_E2E_ACTIVITY = "activity"
        const val EXTRA_E2E_TRANSITION = "transition"
    }
}

private fun com.google.android.gms.location.ActivityTransitionEvent.toInstant(clock: Clock): Instant {
    val elapsedNanos = SystemClock.elapsedRealtimeNanos() - elapsedRealTimeNanos
    val delayMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos.coerceAtLeast(0L))
    return Instant.ofEpochMilli(clock.millis() - delayMillis)
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

private fun String.toMotionActivityOrNull(): MotionActivity? = when (uppercase()) {
    "IN_VEHICLE" -> MotionActivity.IN_VEHICLE
    "STILL" -> MotionActivity.STILL
    "OTHER" -> MotionActivity.OTHER
    else -> null
}

private fun String.toMotionTransitionOrNull(): MotionTransition? = when (uppercase()) {
    "ENTER" -> MotionTransition.ENTER
    "EXIT" -> MotionTransition.EXIT
    "OTHER" -> MotionTransition.OTHER
    else -> null
}
