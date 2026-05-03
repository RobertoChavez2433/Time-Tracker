package com.robertochavez.timetracker.core.common.model

data class AppSettings(
    val minimalActiveNotificationEnabled: Boolean,
    val liveTimerNotificationEnabled: Boolean,
    val privacyDisclosureAccepted: Boolean,
)
