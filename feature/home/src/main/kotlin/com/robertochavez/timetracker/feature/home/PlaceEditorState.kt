@file:Suppress("TooGenericExceptionCaught") // Surface platform/persistence failures without swallowing cancellation.

package com.robertochavez.timetracker.feature.home

import com.robertochavez.timetracker.core.common.model.GeofenceRadiusOptions
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.common.model.normalizedProjectNumberOrNull
import java.time.Clock

enum class PlaceEditorPhase { LOADING, EDITING, SAVING, VALIDATION_ERROR, FAILURE, SAVED }

data class PlaceDraft(
    val name: String = "",
    val job: String = "",
    val address: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val radius: String = WorkLocation.MINIMUM_RADIUS_METERS.toString(),
    val addressResolved: Boolean = true,
) {
    fun values() = arrayListOf(name, job, address, latitude, longitude, radius, addressResolved.toString())
}

data class PlaceEditorState(
    val phase: PlaceEditorPhase = PlaceEditorPhase.LOADING,
    val loaded: Boolean = false,
    val draft: PlaceDraft = PlaceDraft(),
    val isHome: Boolean = false,
    val isNew: Boolean = true,
    val dirty: Boolean = false,
    val resolving: Boolean = false,
    val message: String = "",
    val references: List<MapReferenceLocationUiModel> = emptyList(),
) {
    val busy: Boolean get() = resolving || phase == PlaceEditorPhase.LOADING || phase == PlaceEditorPhase.SAVING
}

internal fun PlaceDraft.validate(clock: Clock, id: String, isHome: Boolean): WorkLocation {
    require(addressResolved) { "Use Find address to update the pin before saving, or choose a map location." }
    require(name.isNotBlank()) { "Enter a place name." }
    val latitude = requireNotNull(latitude.toDoubleOrNull()) { "Choose a location using an address, map, or coordinates." }
    val longitude = requireNotNull(longitude.toDoubleOrNull()) { "Enter a valid longitude." }
    val radius = requireNotNull(radius.toFloatOrNull()) { "Choose a valid radius." }
    require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Coordinates are outside the valid range." }
    if (isHome) require(GeofenceRadiusOptions.all.any { it.meters == radius }) { "Choose a Home radius preset." }
    val job = job.normalizedProjectNumberOrNull()
    if (!isHome) requireNotNull(job) { "Enter a job number." }
    // WorkLocation is used here only to carry validated coordinates; Home retains its own radius presets.
    return WorkLocation(
        latitude,
        longitude,
        if (isHome) WorkLocation.MINIMUM_RADIUS_METERS else radius,
        clock.instant(),
        id,
        name.trim(),
        address.trim().ifBlank { null },
        job,
    )
}

internal fun List<String>.toPlaceDraft() = PlaceDraft(
    this[0],
    this[1],
    this[2],
    this[3],
    this[4],
    this[5],
    getOrNull(6)?.toBoolean() ?: true,
)
