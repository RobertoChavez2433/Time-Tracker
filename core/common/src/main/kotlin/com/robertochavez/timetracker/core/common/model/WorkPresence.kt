package com.robertochavez.timetracker.core.common.model

import java.time.Instant

data class WorkPresence(val atWork: Boolean, val updatedAt: Instant)
