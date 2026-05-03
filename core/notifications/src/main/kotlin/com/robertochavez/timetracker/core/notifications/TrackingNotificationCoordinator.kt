package com.robertochavez.timetracker.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingNotificationCoordinator @Inject constructor(@ApplicationContext private val context: Context) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ACTIVE_TRACKING_CHANNEL_ID,
            "Active tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Optional status for active away-from-home tracking."
        }
        manager.createNotificationChannel(channel)
    }

    fun activeNotificationPolicy(): TrackingNotificationPolicy = TrackingNotificationPolicy()

    fun cancelActiveNotification() {
        context.getSystemService(NotificationManager::class.java).cancel(ACTIVE_TRACKING_NOTIFICATION_ID)
    }

    fun buildMinimalActiveNotification(): Notification.Builder {
        ensureChannels()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, ACTIVE_TRACKING_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }.setContentTitle("Time Tracker")
            .setContentText("Away session tracking is active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
    }

    companion object {
        const val ACTIVE_TRACKING_CHANNEL_ID = "active_tracking"
        const val ACTIVE_TRACKING_NOTIFICATION_ID = 5100
    }
}
