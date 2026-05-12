package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.WorkPresence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

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

    @Test
    fun `drive requires outside home and work geofences`() {
        val bucket = DriveClassificationPolicy.classify(
            activity = MotionActivity.IN_VEHICLE,
            transition = MotionTransition.ENTER,
            homePresence = GeofencePresence.OUTSIDE,
            workPresence = GeofencePresence.OUTSIDE,
        )

        assertEquals(ActivityBucket.DRIVE, bucket)
    }

    @Test
    fun `vehicle motion inside work geofence is not drive`() {
        val bucket = DriveClassificationPolicy.classify(
            activity = MotionActivity.IN_VEHICLE,
            transition = MotionTransition.ENTER,
            homePresence = GeofencePresence.OUTSIDE,
            workPresence = GeofencePresence.INSIDE,
        )

        assertEquals(ActivityBucket.UNCLASSIFIED, bucket)
    }

    @Test
    fun `missing geofence presence does not classify vehicle motion as drive`() {
        val bucket = DriveClassificationPolicy.classify(
            activity = MotionActivity.IN_VEHICLE,
            transition = MotionTransition.ENTER,
            homePresence = GeofencePresence.OUTSIDE,
            workPresence = GeofencePresence.UNKNOWN,
        )

        assertEquals(ActivityBucket.UNCLASSIFIED, bucket)
    }

    @Test
    fun `stale away-from-work presence is unknown`() {
        val eventAt = Instant.parse("2026-05-04T12:00:00Z")
        val presence = WorkPresence(
            atWork = false,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
        )

        val workPresence = DriveClassificationPolicy.workPresenceFrom(presence, eventAt)

        assertEquals(GeofencePresence.UNKNOWN, workPresence)
    }
}
