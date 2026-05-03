package com.robertochavez.timetracker.core.common.model

import java.time.Instant

data class AwaySession(
    val id: String,
    val start: Instant,
    val end: Instant?,
    val countsTowardTotals: Boolean = true,
    val manuallyAdjusted: Boolean = false,
    val drivenMiles: Double = 0.0,
) {
    init {
        require(end == null || end > start) { "Away session end must be after start." }
        require(drivenMiles >= 0.0) { "Driven miles cannot be negative." }
    }

    val isActive: Boolean
        get() = end == null
}
