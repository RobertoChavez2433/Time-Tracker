package com.robertochavez.timetracker.core.testing

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.TrackingSessionController
import java.time.Instant

class FakeTrackingSessionController : TrackingSessionController {
    val startedAt = mutableListOf<Instant>()
    val stoppedAt = mutableListOf<Instant>()
    val activityTransitions = mutableListOf<Pair<ActivityBucket, Instant>>()
    val drivenDistanceUpdates = mutableListOf<Double>()

    override suspend fun startAwaySessionIfTrackable(at: Instant): AwaySession {
        startedAt += at
        return AwaySession(id = "fake-${startedAt.size}", start = at, end = null)
    }

    override suspend fun stopActiveAwaySession(at: Instant): AwaySession? {
        stoppedAt += at
        return null
    }

    override suspend fun recordActivityTransition(bucket: ActivityBucket, at: Instant) {
        activityTransitions += bucket to at
    }

    override suspend fun addDrivenDistanceToActiveSession(distanceMeters: Double, at: Instant): AwaySession? {
        drivenDistanceUpdates += distanceMeters
        return null
    }
}

class FakeHomeLocationSource(private var location: HomeLocation? = null) {
    fun set(location: HomeLocation) {
        this.location = location
    }

    fun current(): HomeLocation? = location
}
