package com.robertochavez.timetracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.model.GeofenceRadiusOptions
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.common.repository.HomeLocationRepository
import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import com.robertochavez.timetracker.core.location.AddressGeocoder
import com.robertochavez.timetracker.core.location.CurrentGeofenceLocationProvider
import com.robertochavez.timetracker.core.location.geofence.HomeGeofenceRegistrar
import com.robertochavez.timetracker.core.location.geofence.WorkGeofenceRegistrar
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.logging.warn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@Suppress("LargeClass", "LongParameterList", "TooManyFunctions")
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeLocationRepository: HomeLocationRepository,
    private val workLocationRepository: WorkLocationRepository,
    private val currentGeofenceLocationProvider: CurrentGeofenceLocationProvider,
    private val addressGeocoder: AddressGeocoder,
    private val homeGeofenceRegistrar: HomeGeofenceRegistrar,
    private val workGeofenceRegistrar: WorkGeofenceRegistrar,
    private val clock: Clock,
    private val logger: AppLogger,
) : ViewModel() {
    private val homeEditorState = MutableStateFlow(HomeEditorState())
    private val workEditorState = MutableStateFlow(WorkEditorState())
    private val statusMessage = MutableStateFlow("")
    private val currentMapReference = MutableStateFlow<MapReferenceLocationUiModel?>(null)

    init {
        viewModelScope.launch {
            homeLocationRepository.observeHomeLocation().collect { home ->
                if (home == null) {
                    if (!homeEditorState.value.hasCoordinates()) {
                        homeEditorState.value = HomeEditorState()
                    }
                } else if (!homeEditorState.value.hasCoordinates()) {
                    homeEditorState.value = HomeEditorState.fromLocation(home)
                }
            }
        }
        viewModelScope.launch {
            workLocationRepository.observeWorkLocations().collect { workLocations ->
                val work = workLocations.firstOrNull()
                if (work == null) {
                    if (workEditorState.value.isPristine()) {
                        workEditorState.value = WorkEditorState()
                    }
                } else if (workEditorState.value.isPristine()) {
                    workEditorState.value = WorkEditorState.fromLocation(work)
                }
            }
        }
    }

    private val locations = combine(
        homeLocationRepository.observeHomeLocation(),
        workLocationRepository.observeWorkLocations(),
    ) { home, workLocations ->
        Locations(home, workLocations)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        locations,
        homeEditorState,
        workEditorState,
        statusMessage,
        currentMapReference,
    ) { locations, homeEditor, workEditor, status, currentReference ->
        val home = locations.home
        val workLocations = locations.work
        val latestWork = workLocations.firstOrNull()
        HomeUiState(
            homeSummary = home?.summary() ?: "No home location set",
            homeSet = home != null,
            workSummary = workLocations.summary(),
            workLocations = workLocations.map { it.toSavedPlaceUiModel() },
            mapReferenceLocations = buildList {
                currentReference?.let(::add)
                home?.let {
                    add(
                        MapReferenceLocationUiModel(
                            id = MapReferenceLocationUiModel.CURRENT_HOME_ID,
                            label = "Home",
                            latitude = it.latitude,
                            longitude = it.longitude,
                        ),
                    )
                }
                workLocations.mapTo(this) { it.toMapReferenceLocationUiModel() }
            },
            workLocationCount = workLocations.size,
            homeLatitude = homeEditor.latitude,
            homeLongitude = homeEditor.longitude,
            homeRadiusMeters = homeEditor.radiusMeters,
            homeAddress = homeEditor.address,
            homeResolvedAddress = homeEditor.resolvedAddress,
            workLatitude = workEditor.latitude,
            workLongitude = workEditor.longitude,
            workRadiusMeters = workEditor.radiusMeters,
            workLabel = workEditor.label,
            workAddress = workEditor.address,
            workResolvedAddress = workEditor.resolvedAddress,
            latestWorkLocationLabel = latestWork?.label.orEmpty(),
            statusMessage = status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun updateHomeField(field: LocationField, value: String) {
        homeEditorState.value = homeEditorState.value.updated(field, value)
    }

    fun updateWorkField(field: LocationField, value: String) {
        workEditorState.value = workEditorState.value.updated(field, value)
    }

    fun updateHomeRadius(radiusMeters: Float) {
        homeEditorState.value = homeEditorState.value.copy(radiusMeters = radiusMeters.toString())
    }

    fun updateWorkRadius(radiusMeters: Float) {
        workEditorState.value = workEditorState.value.copy(radiusMeters = radiusMeters.toString())
    }

    fun beginAddPlace() {
        workEditorState.value = WorkEditorState()
        statusMessage.value = ""
    }

    fun prepareHomeMapFromCurrentLocation(onReady: (Double?, Double?) -> Unit) {
        logger.info(LogCategory.UI, "Current-location home map center requested")
        statusMessage.value = "Finding your current location for the map..."
        currentMapReference.value = null
        viewModelScope.launch {
            runCatching {
                currentGeofenceLocationProvider.currentPreciseHomeLocation(homeEditorState.value.radiusInput())
            }.onSuccess { current ->
                if (current == null) {
                    statusMessage.value =
                        "Current location was unavailable. You can still position the Home pin manually."
                    onReady(null, null)
                } else {
                    currentMapReference.value = MapReferenceLocationUiModel(
                        id = MapReferenceLocationUiModel.CURRENT_LOCATION_ID,
                        label = "Current location",
                        latitude = current.latitude,
                        longitude = current.longitude,
                    )
                    homeEditorState.value = homeEditorState.value.copy(
                        latitude = current.latitude.toString(),
                        longitude = current.longitude.toString(),
                    )
                    statusMessage.value = "Map centered on your current location. Move the Home pin if needed."
                    onReady(current.latitude, current.longitude)
                }
            }.onFailure { error ->
                currentMapReference.value = null
                logger.warn(LogCategory.LOCATION, "Current-location home map center failed", error = error)
                statusMessage.value = error.message ?: "Current location was unavailable."
                onReady(null, null)
            }
        }
    }

    fun resolveHomeAddress() {
        val query = homeEditorState.value.address.trim()
        statusMessage.value = "Looking up home address..."
        viewModelScope.launch {
            runCatching {
                addressGeocoder.findAddress(query)
            }.onSuccess { result ->
                if (result == null) {
                    statusMessage.value = "No address match found."
                } else {
                    homeEditorState.value = homeEditorState.value.copy(
                        address = result.displayAddress,
                        resolvedAddress = result.displayAddress,
                        latitude = result.latitude.toString(),
                        longitude = result.longitude.toString(),
                    )
                    statusMessage.value = "Address found. Review the Home pin and radius before saving."
                }
            }.onFailure { error ->
                logger.warn(LogCategory.LOCATION, "Home address lookup failed", error = error)
                statusMessage.value = error.message ?: "Address lookup failed."
            }
        }
    }

    fun selectHomeMapPin(latitude: Double, longitude: Double, radiusMeters: Float) {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            statusMessage.value = "The selected Home map pin is invalid. Enter coordinates manually."
            return
        }
        homeEditorState.value = homeEditorState.value.copy(
            latitude = latitude.toString(),
            longitude = longitude.toString(),
            radiusMeters = GeofenceRadiusOptions.nearest(radiusMeters).meters.toString(),
        )
        statusMessage.value = "Home map pin added to the editor. Review it, then save Home."
        viewModelScope.launch {
            runCatching {
                addressGeocoder.reverseGeocode(latitude, longitude)
            }.onSuccess { address ->
                if (!address.isNullOrBlank()) {
                    homeEditorState.value = homeEditorState.value.copy(
                        address = address,
                        resolvedAddress = address,
                    )
                    statusMessage.value = "Address found. Review the Home draft, then explicitly save it."
                }
            }.onFailure { error ->
                logger.warn(LogCategory.LOCATION, "Home map pin reverse lookup failed", error = error)
                statusMessage.value = "Address lookup was unavailable. The Home coordinates can still be saved."
            }
        }
    }

    fun cancelHomeEdit() {
        viewModelScope.launch {
            homeEditorState.value = homeLocationRepository.getHomeLocation()
                ?.let(HomeEditorState::fromLocation)
                ?: HomeEditorState()
            statusMessage.value = ""
        }
    }

    fun prepareWorkMapFromCurrentLocation(onReady: (Double?, Double?) -> Unit) {
        logger.info(LogCategory.UI, "Current-location map center requested")
        statusMessage.value = "Finding your current location for the map..."
        currentMapReference.value = null
        viewModelScope.launch {
            runCatching {
                currentGeofenceLocationProvider.currentPreciseWorkLocation(workEditorState.value.radiusInput())
            }.onSuccess { current ->
                if (current == null) {
                    currentMapReference.value = null
                    statusMessage.value =
                        "Current location was unavailable. You can still position the pin manually."
                    onReady(null, null)
                } else {
                    currentMapReference.value = MapReferenceLocationUiModel(
                        id = MapReferenceLocationUiModel.CURRENT_LOCATION_ID,
                        label = "Current location",
                        latitude = current.latitude,
                        longitude = current.longitude,
                    )
                    workEditorState.value = workEditorState.value.copy(
                        latitude = current.latitude.toString(),
                        longitude = current.longitude.toString(),
                    )
                    statusMessage.value = "Map centered on your current location. Move the pin if needed."
                    onReady(current.latitude, current.longitude)
                }
            }.onFailure { error ->
                currentMapReference.value = null
                logger.warn(LogCategory.LOCATION, "Current-location map center failed", error = error)
                statusMessage.value = error.message ?: "Current location was unavailable."
                onReady(null, null)
            }
        }
    }

    fun useCurrentHomeLocation() {
        logger.info(LogCategory.UI, "Use current home location requested")
        statusMessage.value = "Getting current home location..."
        viewModelScope.launch {
            runCatching {
                currentGeofenceLocationProvider.currentPreciseHomeLocation(homeEditorState.value.radiusInput())
            }.onSuccess { home ->
                if (home == null) {
                    statusMessage.value =
                        "Precise current location was unavailable. Grant precise location before setting home automatically."
                } else {
                    statusMessage.value = saveHome(home)
                    homeEditorState.value = HomeEditorState.fromLocation(home)
                }
            }.onFailure { error ->
                logger.warn(LogCategory.LOCATION, "Use current home location failed", error = error)
                statusMessage.value = error.message ?: "Location permission or service unavailable."
            }
        }
    }

    fun useCurrentWorkLocation(replaceLatest: Boolean = false) {
        logger.info(LogCategory.UI, "Use current work location requested")
        statusMessage.value = "Getting current work location..."
        viewModelScope.launch {
            runCatching {
                val target = resolveWorkTarget(replaceLatest)
                val address = workEditorState.value.preferredAddress()
                currentGeofenceLocationProvider.currentPreciseWorkLocation(workEditorState.value.radiusInput())
                    ?.copy(id = target.id, label = target.label, address = address)
            }.onSuccess { work ->
                if (work == null) {
                    statusMessage.value =
                        "Precise current location was unavailable. Grant precise location before setting work automatically."
                } else {
                    statusMessage.value = saveWork(work)
                    workEditorState.value = WorkEditorState.fromLocation(work)
                }
            }.onFailure { error ->
                logger.warn(LogCategory.LOCATION, "Use current work location failed", error = error)
                statusMessage.value = error.message ?: "Location permission or service unavailable."
            }
        }
    }

    fun saveHomePin() {
        logger.info(LogCategory.UI, "Save home pin requested")
        viewModelScope.launch {
            homeEditorState.value.toHomeLocation(clock).fold(
                onSuccess = { home ->
                    statusMessage.value = saveHome(home)
                },
                onFailure = { error ->
                    logger.warn(LogCategory.LOCATION, "Save home pin failed", error = error)
                    statusMessage.value = error.message ?: "Invalid home pin."
                },
            )
        }
    }

    fun saveWorkPin(replaceLatest: Boolean = false) {
        logger.info(LogCategory.UI, "Save work pin requested")
        viewModelScope.launch {
            val target = resolveWorkTarget(replaceLatest)
            workEditorState.value.toWorkLocation(clock, target.id, target.label).fold(
                onSuccess = { work ->
                    statusMessage.value = saveWork(work)
                },
                onFailure = { error ->
                    logger.warn(LogCategory.LOCATION, "Save work pin failed", error = error)
                    statusMessage.value = error.message ?: "Invalid work pin."
                },
            )
        }
    }

    fun resolveWorkAddress() {
        val query = workEditorState.value.address.trim()
        statusMessage.value = "Looking up work address..."
        viewModelScope.launch {
            runCatching {
                addressGeocoder.findAddress(query)
            }.onSuccess { result ->
                if (result == null) {
                    statusMessage.value = "No address match found."
                } else {
                    workEditorState.value = workEditorState.value.copy(
                        address = result.displayAddress,
                        resolvedAddress = result.displayAddress,
                        latitude = result.latitude.toString(),
                        longitude = result.longitude.toString(),
                    )
                    statusMessage.value = "Address found. Confirm the pin, radius, and site name before saving."
                }
            }.onFailure { error ->
                logger.warn(LogCategory.LOCATION, "Work address lookup failed", error = error)
                statusMessage.value = error.message ?: "Address lookup failed."
            }
        }
    }

    fun selectWorkMapPin(latitude: Double, longitude: Double, radiusMeters: Float) {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            statusMessage.value = "The selected map pin is invalid. Enter coordinates manually."
            return
        }
        workEditorState.value = workEditorState.value.copy(
            latitude = latitude.toString(),
            longitude = longitude.toString(),
            radiusMeters = radiusMeters.toString(),
        )
        statusMessage.value = "Map pin added to the editor. Review it, then choose Add or Replace to save."
        viewModelScope.launch {
            runCatching {
                addressGeocoder.reverseGeocode(latitude, longitude)
            }.onSuccess { address ->
                if (!address.isNullOrBlank()) {
                    workEditorState.value = workEditorState.value.copy(
                        address = address,
                        resolvedAddress = address,
                    )
                    statusMessage.value = "Address found. Review the draft, then explicitly save it."
                }
            }.onFailure { error ->
                logger.warn(LogCategory.LOCATION, "Map pin reverse lookup failed", error = error)
                statusMessage.value = "Address lookup was unavailable. The coordinates can still be saved manually."
            }
        }
    }

    private suspend fun saveHome(homeLocation: HomeLocation): String {
        homeLocationRepository.setHomeLocation(homeLocation)
        return runCatching {
            homeGeofenceRegistrar.registerHomeGeofence(homeLocation)
        }.fold(
            onSuccess = {
                logger.info(LogCategory.LOCATION, "Home saved and geofence registered")
                "Home saved and geofence registered."
            },
            onFailure = { error ->
                logger.warn(LogCategory.LOCATION, "Home saved but geofence registration failed", error = error)
                "Home saved. ${error.message ?: "Geofence could not be registered."}"
            },
        )
    }

    private suspend fun saveWork(workLocation: WorkLocation): String {
        workLocationRepository.setWorkLocation(workLocation)
        return runCatching {
            workGeofenceRegistrar.registerWorkGeofences(workLocationRepository.getWorkLocations())
        }.fold(
            onSuccess = {
                logger.info(LogCategory.LOCATION, "Work saved and geofence registered")
                "Work location saved and geofences registered."
            },
            onFailure = { error ->
                logger.warn(LogCategory.LOCATION, "Work saved but geofence registration failed", error = error)
                "Work location saved. ${error.message ?: "Geofence could not be registered."}"
            },
        )
    }

    private suspend fun resolveWorkTarget(replaceLatest: Boolean): WorkTarget {
        val workLocations = workLocationRepository.getWorkLocations()
        val latest = workLocations.firstOrNull()
        val requestedLabel = workEditorState.value.label.trim()
        return if (replaceLatest && latest != null) {
            WorkTarget(latest.id, requestedLabel.ifBlank { latest.label })
        } else {
            val index = workLocations.size + 1
            val defaultLabel = if (workLocations.isEmpty()) WorkLocation.DEFAULT_LABEL else "Work site $index"
            val label = requestedLabel
                .takeUnless { it.isBlank() || (latest != null && it == latest.label) }
                ?: defaultLabel
            WorkTarget(
                id = if (workLocations.isEmpty()) WorkLocation.DEFAULT_ID else "work-${clock.millis()}-$index",
                label = label,
            )
        }
    }
}

data class HomeUiState(
    val homeSummary: String = "No home location set",
    val homeSet: Boolean = false,
    val workSummary: String = "No work location set",
    val workLocations: List<SavedPlaceUiModel> = emptyList(),
    val mapReferenceLocations: List<MapReferenceLocationUiModel> = emptyList(),
    val workLocationCount: Int = 0,
    val homeLatitude: String = "",
    val homeLongitude: String = "",
    val homeRadiusMeters: String = HomeLocation.MINIMUM_RADIUS_METERS.toString(),
    val homeAddress: String = "",
    val homeResolvedAddress: String = "",
    val workLatitude: String = "",
    val workLongitude: String = "",
    val workRadiusMeters: String = WorkLocation.MINIMUM_RADIUS_METERS.toString(),
    val workLabel: String = WorkLocation.DEFAULT_LABEL,
    val workAddress: String = "",
    val workResolvedAddress: String = "",
    val latestWorkLocationLabel: String = "",
    val statusMessage: String = "",
)

private data class WorkTarget(val id: String, val label: String)

data class SavedPlaceUiModel(val id: String, val label: String, val address: String?, val radius: String)

data class MapReferenceLocationUiModel(val id: String, val label: String, val latitude: Double, val longitude: Double) {
    companion object {
        const val CURRENT_LOCATION_ID = "current-location"
        const val CURRENT_HOME_ID = "home"
    }
}

private data class Locations(val home: HomeLocation?, val work: List<WorkLocation>)

private fun WorkLocation.toSavedPlaceUiModel(): SavedPlaceUiModel = SavedPlaceUiModel(
    id = id,
    label = label,
    address = address,
    radius = radiusLabel(),
)

private fun WorkLocation.toMapReferenceLocationUiModel(): MapReferenceLocationUiModel = MapReferenceLocationUiModel(
    id = "work:$id",
    label = label,
    latitude = latitude,
    longitude = longitude,
)

private fun WorkEditorState.preferredAddress(): String? = resolvedAddress.trim().ifBlank { address.trim() }.ifBlank { null }

private fun List<WorkLocation>.summary(): String = when (size) {
    0 -> "No work location set"
    1 -> first().summary()
    else -> "$size work locations saved"
}
