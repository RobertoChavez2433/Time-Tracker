package com.robertochavez.timetracker.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal fun Context.hasForegroundLocationPermission(): Boolean = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
    hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

internal fun Context.hasFineLocationPermission(): Boolean = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

internal fun Context.hasActivityRecognitionPermission(): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Manifest.permission.ACTIVITY_RECOGNITION
    } else {
        PLAY_SERVICES_ACTIVITY_RECOGNITION_PERMISSION
    }
    return hasPermission(permission)
}

private fun Context.hasPermission(permission: String): Boolean = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private const val PLAY_SERVICES_ACTIVITY_RECOGNITION_PERMISSION = "com.google.android.gms.permission.ACTIVITY_RECOGNITION"
