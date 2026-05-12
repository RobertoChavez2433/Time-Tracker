package com.robertochavez.timetracker.feature.home

import com.robertochavez.timetracker.core.common.model.GeofenceRadiusOptions
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkLocation
import java.time.Clock
import java.time.Instant

enum class LocationField {
    LABEL,
    LATITUDE,
    LONGITUDE,
    RADIUS_METERS,
}

internal data class HomeEditorState(
    val latitude: String = "",
    val longitude: String = "",
    val radiusMeters: String = GeofenceRadiusOptions.default.meters.toString(),
) {
    companion object {
        fun fromLocation(homeLocation: HomeLocation): HomeEditorState = HomeEditorState(
            latitude = homeLocation.latitude.toString(),
            longitude = homeLocation.longitude.toString(),
            radiusMeters = GeofenceRadiusOptions.nearest(homeLocation.radiusMeters).meters.toString(),
        )
    }
}

internal fun HomeEditorState.hasCoordinates(): Boolean = latitude.isNotBlank() || longitude.isNotBlank()

internal fun HomeEditorState.updated(field: LocationField, value: String): HomeEditorState = when (field) {
    LocationField.LABEL -> this
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

internal fun HomeEditorState.radiusInput(): Float = (radiusMeters.toFloatOrNull() ?: HomeLocation.MINIMUM_RADIUS_METERS)
    .coerceAtLeast(HomeLocation.MINIMUM_RADIUS_METERS)

internal data class WorkEditorState(
    val label: String = WorkLocation.DEFAULT_LABEL,
    val latitude: String = "",
    val longitude: String = "",
    val radiusMeters: String = GeofenceRadiusOptions.default.meters.toString(),
) {
    companion object {
        fun fromLocation(workLocation: WorkLocation): WorkEditorState = WorkEditorState(
            label = workLocation.label,
            latitude = workLocation.latitude.toString(),
            longitude = workLocation.longitude.toString(),
            radiusMeters = GeofenceRadiusOptions.nearest(workLocation.radiusMeters).meters.toString(),
        )
    }
}

internal fun WorkEditorState.hasCoordinates(): Boolean = latitude.isNotBlank() || longitude.isNotBlank()

internal fun WorkEditorState.updated(field: LocationField, value: String): WorkEditorState = when (field) {
    LocationField.LABEL -> copy(label = value)
    LocationField.LATITUDE -> copy(latitude = value)
    LocationField.LONGITUDE -> copy(longitude = value)
    LocationField.RADIUS_METERS -> copy(radiusMeters = value)
}

internal fun WorkEditorState.toWorkLocation(clock: Clock, id: String, label: String): Result<WorkLocation> = runCatching {
    WorkLocation(
        latitude = requireNotNull(latitude.toDoubleOrNull()) { "Enter valid work latitude, longitude, and radius." },
        longitude = requireNotNull(longitude.toDoubleOrNull()) { "Enter valid work latitude, longitude, and radius." },
        radiusMeters = requireNotNull(radiusMeters.toFloatOrNull()) { "Enter valid work latitude, longitude, and radius." },
        updatedAt = Instant.now(clock),
        id = id,
        label = label.trim(),
    )
}

internal fun WorkEditorState.radiusInput(): Float = (radiusMeters.toFloatOrNull() ?: WorkLocation.MINIMUM_RADIUS_METERS)
    .coerceIn(WorkLocation.MINIMUM_RADIUS_METERS, WorkLocation.MAXIMUM_RADIUS_METERS)

internal fun HomeLocation.summary(): String = "${latitude.formatCoordinate()}, ${longitude.formatCoordinate()} (${radiusLabel()})"

internal fun WorkLocation.summary(): String = "$label: ${latitude.formatCoordinate()}, ${longitude.formatCoordinate()} (${radiusLabel()})"

internal fun HomeLocation.radiusLabel(): String = GeofenceRadiusOptions.nearest(radiusMeters).label

internal fun WorkLocation.radiusLabel(): String = GeofenceRadiusOptions.nearest(radiusMeters).label

private fun Double.formatCoordinate(): String = "%.5f".format(this)
