package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityBucketPolicyTest {
    @Test
    fun `in vehicle enter is drive when away from work`() {
        val bucket = ActivityBucketPolicy.classify(
            activity = MotionActivity.IN_VEHICLE,
            transition = MotionTransition.ENTER,
            atWork = false,
        )

        assertEquals(ActivityBucket.DRIVE, bucket)
    }

    @Test
    fun `in vehicle enter is unclassified while at work`() {
        val bucket = ActivityBucketPolicy.classify(
            activity = MotionActivity.IN_VEHICLE,
            transition = MotionTransition.ENTER,
            atWork = true,
        )

        assertEquals(ActivityBucket.UNCLASSIFIED, bucket)
    }
}
