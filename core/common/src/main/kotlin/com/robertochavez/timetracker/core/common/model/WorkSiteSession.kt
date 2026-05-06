package com.robertochavez.timetracker.core.common.model

import java.time.Instant

data class WorkSiteSession(val id: String, val workLocationId: String, val start: Instant, val end: Instant?) {
    init {
        require(end == null || end > start) { "Work site session end must be after start." }
    }

    val isActive: Boolean = end == null
}
