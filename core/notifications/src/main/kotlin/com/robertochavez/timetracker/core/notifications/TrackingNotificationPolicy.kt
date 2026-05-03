package com.robertochavez.timetracker.core.notifications

data class TrackingNotificationPolicy(
    val minimalActiveNotificationEnabled: Boolean = false,
    val liveTimerNotificationEnabled: Boolean = false,
) {
    val shouldShowActiveNotification: Boolean
        get() = minimalActiveNotificationEnabled || liveTimerNotificationEnabled
}
