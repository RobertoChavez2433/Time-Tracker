package com.robertochavez.timetracker.core.common.model

import java.time.Instant

interface TrackingSessionController {
    suspend fun startAwaySessionIfTrackable(at: Instant): AwaySession?

    suspend fun stopActiveAwaySession(at: Instant): AwaySession?

    suspend fun recordActivityTransition(bucket: ActivityBucket, at: Instant)
}
