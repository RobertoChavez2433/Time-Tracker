package com.robertochavez.timetracker.core.common.model

import java.time.Instant

data class ActivityInterval(
    val id: String,
    val sessionId: String,
    val bucket: ActivityBucket,
    val start: Instant,
    val end: Instant,
) {
    init {
        require(end > start) { "Activity interval end must be after start." }
    }
}
