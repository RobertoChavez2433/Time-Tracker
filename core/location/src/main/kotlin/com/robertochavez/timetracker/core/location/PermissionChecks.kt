package com.robertochavez.timetracker.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

fun Context.hasFineLocationPermission(): Boolean = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

fun Context.hasBackgroundLocationPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
    hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

fun Context.hasActivityRecognitionPermission(): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Manifest.permission.ACTIVITY_RECOGNITION
    } else {
        PLAY_SERVICES_ACTIVITY_RECOGNITION_PERMISSION
    }
    return hasPermission(permission)
}

fun Context.hasPostNotificationsPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    hasPermission(Manifest.permission.POST_NOTIFICATIONS)

private fun Context.hasPermission(permission: String): Boolean = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private const val PLAY_SERVICES_ACTIVITY_RECOGNITION_PERMISSION = "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
