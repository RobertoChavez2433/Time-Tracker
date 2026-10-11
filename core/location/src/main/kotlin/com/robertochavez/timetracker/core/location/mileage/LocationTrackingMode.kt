package com.robertochavez.timetracker.core.location.mileage

import android.content.SharedPreferences
import com.google.android.gms.location.Priority
import com.robertochavez.timetracker.core.common.domain.DriveMileagePoint
import java.time.Instant

internal enum class LocationTrackingMode(
    val priority: Int,
    val updateIntervalMillis: Long,
    val minUpdateIntervalMillis: Long,
    val maxUpdateDelayMillis: Long,
    val minUpdateDistanceMeters: Float,
) {
    BALANCED(
        priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        updateIntervalMillis = 60_000L,
        minUpdateIntervalMillis = 30_000L,
        maxUpdateDelayMillis = 120_000L,
        minUpdateDistanceMeters = 50f,
    ),
    VEHICLE(
        priority = Priority.PRIORITY_HIGH_ACCURACY,
        updateIntervalMillis = 15_000L,
        minUpdateIntervalMillis = 5_000L,
        maxUpdateDelayMillis = 30_000L,
        minUpdateDistanceMeters = 20f,
    ),
}

internal fun SharedPreferences.lastPoint(): DriveMileagePoint? {
    val latitude = getString(KEY_LAST_LATITUDE, null)?.toDoubleOrNull()
    val longitude = getString(KEY_LAST_LONGITUDE, null)?.toDoubleOrNull()
    val atEpochMillis = getLong(KEY_LAST_AT_EPOCH_MILLIS, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
    return if (latitude == null || longitude == null || atEpochMillis == null) {
        null
    } else {
        DriveMileagePoint(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = getFloat(KEY_LAST_ACCURACY, Float.NaN).takeUnless { it.isNaN() },
            at = Instant.ofEpochMilli(atEpochMillis),
        )
    }
}

internal fun SharedPreferences.Editor.putLastPoint(point: DriveMileagePoint?): SharedPreferences.Editor {
    if (point == null) return clearLastPoint()
    putString(KEY_LAST_LATITUDE, point.latitude.toString())
    putString(KEY_LAST_LONGITUDE, point.longitude.toString())
    putLong(KEY_LAST_AT_EPOCH_MILLIS, point.at.toEpochMilli())
    val accuracyMeters = point.accuracyMeters
    if (accuracyMeters == null) {
        remove(KEY_LAST_ACCURACY)
    } else {
        putFloat(KEY_LAST_ACCURACY, accuracyMeters)
    }
    return this
}

internal fun SharedPreferences.Editor.clearLastPoint(): SharedPreferences.Editor {
    remove(KEY_LAST_LATITUDE)
    remove(KEY_LAST_LONGITUDE)
    remove(KEY_LAST_ACCURACY)
    remove(KEY_LAST_AT_EPOCH_MILLIS)
    return this
}

internal fun SharedPreferences.trackingMode(): LocationTrackingMode? =
    getString(KEY_TRACKING_MODE, null)?.let { name -> LocationTrackingMode.entries.firstOrNull { it.name == name } }

internal fun SharedPreferences.Editor.putTrackingMode(mode: LocationTrackingMode): SharedPreferences.Editor =
    putString(KEY_TRACKING_MODE, mode.name)

internal fun SharedPreferences.Editor.clearTrackingMode(): SharedPreferences.Editor = remove(KEY_TRACKING_MODE)

internal fun SharedPreferences.Editor.clearMovementInference(): SharedPreferences.Editor =
    remove(KEY_INFERRED_DRIVE_CANDIDATE_START_EPOCH_MILLIS).remove(KEY_INFERRED_DRIVE_ACTIVE)

private const val KEY_TRACKING_MODE = "tracking_mode"
private const val KEY_LAST_LATITUDE = "last_latitude"
private const val KEY_LAST_LONGITUDE = "last_longitude"
private const val KEY_LAST_ACCURACY = "last_accuracy"
private const val KEY_LAST_AT_EPOCH_MILLIS = "last_at_epoch_millis"
private const val KEY_INFERRED_DRIVE_CANDIDATE_START_EPOCH_MILLIS = "inferred_drive_candidate_start_epoch_millis"
private const val KEY_INFERRED_DRIVE_ACTIVE = "inferred_drive_active"
