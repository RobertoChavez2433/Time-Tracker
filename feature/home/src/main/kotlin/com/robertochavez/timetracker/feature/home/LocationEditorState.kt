package com.robertochavez.timetracker.feature.home

import com.robertochavez.timetracker.core.common.model.GeofenceRadiusOptions
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkLocation
import java.time.Clock
import java.time.Instant
import kotlin.math.roundToInt

enum class LocationField {
    LABEL,
    ADDRESS,
    LATITUDE,
    LONGITUDE,
    RADIUS_METERS,
}

internal data class HomeEditorState(
    val address: String = "",
    val resolvedAddress: String = "",
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
    LocationField.ADDRESS -> copy(address = value, resolvedAddress = "")
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
    val address: String = "",
    val resolvedAddress: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val radiusMeters: String = WorkLocation.MINIMUM_RADIUS_METERS.toString(),
) {
    companion object {
        fun fromLocation(workLocation: WorkLocation): WorkEditorState = WorkEditorState(
            label = workLocation.label,
            address = workLocation.address.orEmpty(),
            resolvedAddress = workLocation.address.orEmpty(),
            latitude = workLocation.latitude.toString(),
            longitude = workLocation.longitude.toString(),
            radiusMeters = workLocation.radiusMeters
                .coerceIn(WorkLocation.MINIMUM_RADIUS_METERS, WorkLocation.MAXIMUM_RADIUS_METERS)
                .toString(),
        )
    }
}

internal fun WorkEditorState.hasCoordinates(): Boolean = latitude.isNotBlank() || longitude.isNotBlank()

internal fun WorkEditorState.isPristine(): Boolean = !hasCoordinates() &&
    label == WorkLocation.DEFAULT_LABEL &&
    address.isBlank() &&
    resolvedAddress.isBlank()

internal fun WorkEditorState.updated(field: LocationField, value: String): WorkEditorState = when (field) {
    LocationField.LABEL -> copy(label = value)
    LocationField.ADDRESS -> copy(address = value, resolvedAddress = "")
    LocationField.LATITUDE -> copy(latitude = value)
    LocationField.LONGITUDE -> copy(longitude = value)
    LocationField.RADIUS_METERS -> copy(radiusMeters = value)
}

internal fun WorkEditorState.toWorkLocation(clock: Clock, id: String, label: String): Result<WorkLocation> = runCatching {
    val trimmedResolvedAddress = resolvedAddress.trim()
    val trimmedAddress = address.trim()
    WorkLocation(
        latitude = requireNotNull(latitude.toDoubleOrNull()) { "Enter valid work latitude, longitude, and radius." },
        longitude = requireNotNull(longitude.toDoubleOrNull()) { "Enter valid work latitude, longitude, and radius." },
        radiusMeters = requireNotNull(radiusMeters.toFloatOrNull()) { "Enter valid work latitude, longitude, and radius." },
        updatedAt = Instant.now(clock),
        id = id,
        label = label.trim(),
        address = trimmedResolvedAddress.ifBlank { trimmedAddress }.ifBlank { null },
    )
}

internal fun WorkEditorState.radiusInput(): Float = (radiusMeters.toFloatOrNull() ?: WorkLocation.MINIMUM_RADIUS_METERS)
    .coerceIn(WorkLocation.MINIMUM_RADIUS_METERS, WorkLocation.MAXIMUM_RADIUS_METERS)

internal fun HomeLocation.summary(): String = "${latitude.formatCoordinate()}, ${longitude.formatCoordinate()} (${radiusLabel()})"

internal fun WorkLocation.summary(): String {
    val addressSummary = address?.let { " - $it" }.orEmpty()
    return "$label: ${latitude.formatCoordinate()}, ${longitude.formatCoordinate()} (${radiusLabel()})$addressSummary"
}

internal fun HomeLocation.radiusLabel(): String = GeofenceRadiusOptions.nearest(radiusMeters).label

internal fun WorkLocation.radiusLabel(): String {
    val miles = (radiusMeters / GeofenceRadiusOptions.ONE_MILE_METERS).roundToInt()
    return "$miles ${if (miles == 1) "mile" else "miles"}"
}

private fun Double.formatCoordinate(): String = "%.5f".format(this)
