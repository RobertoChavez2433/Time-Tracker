package com.robertochavez.timetracker.core.common.model

import java.time.Instant

data class WorkSiteSession(
    val id: String,
    val workLocationId: String,
    val start: Instant,
    val end: Instant?,
    val workLocationLabelSnapshot: String = WorkLocation.DEFAULT_LABEL,
    val projectNumberSnapshot: String? = null,
) {
    init {
        require(end == null || end > start) { "Work site session end must be after start." }
        require(workLocationLabelSnapshot.isNotBlank()) { "Work site session label cannot be blank." }
        require(projectNumberSnapshot == null || projectNumberSnapshot == normalizeProjectNumber(projectNumberSnapshot)) {
            "Session project number must be normalized."
        }
    }

    val isActive: Boolean = end == null
}
