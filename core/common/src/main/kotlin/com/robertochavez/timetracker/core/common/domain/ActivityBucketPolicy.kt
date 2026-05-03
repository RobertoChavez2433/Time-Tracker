package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket

enum class MotionActivity {
    IN_VEHICLE,
    STILL,
    OTHER,
}

enum class MotionTransition {
    ENTER,
    EXIT,
    OTHER,
}

object ActivityBucketPolicy {
    fun classify(activity: MotionActivity, transition: MotionTransition, atWork: Boolean): ActivityBucket = when {
        activity == MotionActivity.IN_VEHICLE && transition == MotionTransition.ENTER && atWork -> ActivityBucket.UNCLASSIFIED
        activity == MotionActivity.IN_VEHICLE && transition == MotionTransition.ENTER -> ActivityBucket.DRIVE
        activity == MotionActivity.STILL && transition == MotionTransition.ENTER -> ActivityBucket.IDLE
        transition == MotionTransition.EXIT -> ActivityBucket.UNCLASSIFIED
        else -> ActivityBucket.UNCLASSIFIED
    }
}
