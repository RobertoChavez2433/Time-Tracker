package com.robertochavez.timetracker.feature.home

import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkLocation
import java.time.Clock
import java.time.Instant

enum class LocationField {
    LATITUDE,
    LONGITUDE,
    RADIUS_METERS,
}

internal data class HomeEditorState(
    val latitude: String = "",
    val longitude: String = "",
    val radiusMeters: String = HomeLocation.MINIMUM_RADIUS_METERS.toInt().toString(),
) {
    companion object {
        fun fromLocation(homeLocation: HomeLocation): HomeEditorState = HomeEditorState(
            latitude = homeLocation.latitude.toString(),
            longitude = homeLocation.longitude.toString(),
            radiusMeters = homeLocation.radiusMeters.toInt().toString(),
        )
    }
}

internal fun HomeEditorState.updated(field: LocationField, value: String): HomeEditorState = when (field) {
    LocationField.LATITUDE -> copy(latitude = value)
    LocationField.LONGITUDE -> copy(longitude = value)
    LocationField.RADIUS_METERS -> copy(radiusMeters = value)
}

internal fun HomeEditorState.toHomeLocation(clock: Clock): Result<HomeLocation> = runCatching {
    HomeLocation(
        latitude = requireNotNull(latitude.toDoubleOrNull()) { "Enter valid home latitude, longitude, and radius." },
        longitude = requireNotNull(longitude.toDoubleOrNull()) { "Enter valid home latitude, longitude, and radius." },
        radiusMeters = requireNotNull(radiusMeters.toFloatOrNull()) { "Enter valid home latitude, longitude, and radius." },
        updatedAt = Instant.now(clock),
    )
}

internal fun HomeEditorState.radiusInput(): Float = radiusMeters.toFloatOrNull() ?: HomeLocation.MINIMUM_RADIUS_METERS

internal data class WorkEditorState(
    val latitude: String = "",
    val longitude: String = "",
    val radiusMeters: String = WorkLocation.MINIMUM_RADIUS_METERS.toInt().toString(),
) {
    companion object {
        fun fromLocation(workLocation: WorkLocation): WorkEditorState = WorkEditorState(
            latitude = workLocation.latitude.toString(),
            longitude = workLocation.longitude.toString(),
            radiusMeters = workLocation.radiusMeters.toInt().toString(),
        )
    }
}

internal fun WorkEditorState.updated(field: LocationField, value: String): WorkEditorState = when (field) {
    LocationField.LATITUDE -> copy(latitude = value)
    LocationField.LONGITUDE -> copy(longitude = value)
    LocationField.RADIUS_METERS -> copy(radiusMeters = value)
}

internal fun WorkEditorState.toWorkLocation(clock: Clock): Result<WorkLocation> = runCatching {
    WorkLocation(
        latitude = requireNotNull(latitude.toDoubleOrNull()) { "Enter valid work latitude, longitude, and radius." },
        longitude = requireNotNull(longitude.toDoubleOrNull()) { "Enter valid work latitude, longitude, and radius." },
        radiusMeters = requireNotNull(radiusMeters.toFloatOrNull()) { "Enter valid work latitude, longitude, and radius." },
        updatedAt = Instant.now(clock),
    )
}

internal fun WorkEditorState.radiusInput(): Float = radiusMeters.toFloatOrNull() ?: WorkLocation.MINIMUM_RADIUS_METERS

internal fun HomeLocation.summary(): String = "${latitude.formatCoordinate()}, ${longitude.formatCoordinate()} (${radiusMeters.toInt()} m)"

internal fun WorkLocation.summary(): String = "${latitude.formatCoordinate()}, ${longitude.formatCoordinate()} (${radiusMeters.toInt()} m)"

private fun Double.formatCoordinate(): String = "%.5f".format(this)
