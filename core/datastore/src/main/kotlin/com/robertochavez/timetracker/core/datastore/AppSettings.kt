package com.robertochavez.timetracker.core.datastore

data class AppSettings(
    val minimalActiveNotificationEnabled: Boolean = false,
    val liveTimerNotificationEnabled: Boolean = false,
    val privacyDisclosureAccepted: Boolean = false,
)
