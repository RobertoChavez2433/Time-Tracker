@file:Suppress("TooGenericExceptionCaught") // Surface platform/persistence failures without swallowing cancellation.

package com.robertochavez.timetracker.feature.home

import androidx.lifecycle.SavedStateHandle
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

@Suppress("TooManyFunctions", "LargeClass", "LongParameterList")
@HiltViewModel
class PlaceEditorViewModel @Inject constructor(
    private val homeRepository: HomeLocationRepository,
    private val workRepository: WorkLocationRepository,
    private val locationProvider: CurrentGeofenceLocationProvider,
    private val geocoder: AddressGeocoder,
    private val homeRegistrar: HomeGeofenceRegistrar,
    private val workRegistrar: WorkGeofenceRegistrar,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val requestedId = savedState.get<String>("worksiteId")
    private val isHome = savedState.get<String>("kind") == "home"
    private val id = savedState.get<String>("draftId") ?: UUID.randomUUID().toString().also { savedState["draftId"] = it }
    private val mutableState = MutableStateFlow(PlaceEditorState(isHome = isHome, isNew = requestedId == null))
    val state = mutableState.asStateFlow()
    private var baseline = PlaceDraft()

    init {
        load()
    }

    fun load() {
        mutableState.value = state.value.copy(phase = PlaceEditorPhase.LOADING)
        viewModelScope.launch {
            try {
                val home = homeRepository.getHomeLocation()
                val worksites = workRepository.getWorkLocations()
                val existing = worksites.firstOrNull { it.id == requestedId }
                require(isHome || requestedId == null || existing != null) { "This saved place no longer exists." }
                baseline = initialDraft(home, existing)
                val restored = savedState.get<ArrayList<String>>("draft")?.toPlaceDraft() ?: baseline
                mutableState.value = state.value.copy(
                    phase = PlaceEditorPhase.EDITING,
                    loaded = true,
                    draft = restored,
                    dirty = restored != baseline,
                    isNew = if (isHome) {
                        home == null
                    } else {
                        existing ==
                            null
                    },
                    references = buildList {
                        home?.let { add(MapReferenceLocationUiModel("home", "Home", it.latitude, it.longitude)) }
                        worksites.forEach { add(MapReferenceLocationUiModel("work:${it.id}", it.label, it.latitude, it.longitude)) }
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value =
                    state.value.copy(phase = PlaceEditorPhase.FAILURE, message = error.message ?: "Could not load place.")
            }
        }
    }

    private fun initialDraft(home: HomeLocation?, existing: WorkLocation?): PlaceDraft = when {
        isHome -> PlaceDraft(
            name = "Home",
            latitude = home?.latitude?.toString().orEmpty(),
            longitude = home?.longitude?.toString().orEmpty(),
            radius = (home?.radiusMeters ?: GeofenceRadiusOptions.default.meters).toString(),
        )
        existing != null -> PlaceDraft(
            existing.label,
            existing.projectNumber.orEmpty(),
            existing.address.orEmpty(),
            existing.latitude.toString(),
            existing.longitude.toString(),
            existing.radiusMeters.toString(),
        )
        else -> PlaceDraft()
    }

    fun update(field: LocationField, value: String) {
        if (!state.value.loaded || state.value.busy || state.value.phase == PlaceEditorPhase.SAVED) return
        val draft = state.value.draft
        setDraft(
            when (field) {
                LocationField.LABEL -> draft.copy(name = value)
                LocationField.PROJECT_NUMBER -> draft.copy(job = value)
                LocationField.ADDRESS -> draft.copy(address = value, addressResolved = value.isBlank())
                LocationField.LATITUDE -> draft.copy(latitude = value, address = "", addressResolved = true)
                LocationField.LONGITUDE -> draft.copy(longitude = value, address = "", addressResolved = true)
                LocationField.RADIUS_METERS -> draft.copy(radius = value)
            },
        )
    }

    private fun setDraft(draft: PlaceDraft) {
        savedState["draft"] = draft.values()
        mutableState.value = state.value.copy(draft = draft, dirty = draft != baseline, phase = PlaceEditorPhase.EDITING, message = "")
    }

    fun resolveAddress() = resolve {
        val match =
            requireNotNull(geocoder.findAddress(state.value.draft.address.trim())) {
                "No address match found. Try another address or a map pin."
            }
        setDraft(
            state.value.draft.copy(
                latitude = match.latitude.toString(),
                longitude = match.longitude.toString(),
                address = match.displayAddress,
                addressResolved = true,
            ),
        )
    }

    fun useCurrentLocation() = resolve {
        val radius =
            state.value.draft.radius.toFloatOrNull()
                ?: if (isHome) GeofenceRadiusOptions.default.meters else WorkLocation.MINIMUM_RADIUS_METERS
        val coordinates = if (isHome) {
            locationProvider.currentPreciseHomeLocation(radius)?.let { it.latitude to it.longitude }
        } else {
            locationProvider.currentPreciseWorkLocation(radius)?.let { it.latitude to it.longitude }
        }
        requireNotNull(coordinates) { "Current location is unavailable. Check precise location permission or use the map." }
        setDraft(
            state.value.draft.copy(
                latitude = coordinates.first.toString(),
                longitude = coordinates.second.toString(),
                address = "",
                addressResolved = true,
            ),
        )
    }

    fun selectMapPin(latitude: Double, longitude: Double, radius: Float) {
        if (state.value.busy) return
        setDraft(
            state.value.draft.copy(
                latitude = latitude.toString(),
                longitude = longitude.toString(),
                radius = radius.toString(),
                address = "",
                addressResolved = true,
            ),
        )
        resolve {
            val address = geocoder.reverseGeocode(latitude, longitude)
            if (!address.isNullOrBlank()) setDraft(state.value.draft.copy(address = address, addressResolved = true))
        }
    }

    private fun resolve(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(resolving = true, message = "Finding location…")
        viewModelScope.launch {
            try {
                action()
                mutableState.value = state.value.copy(message = "Location added to draft. Review, then Save.")
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value =
                    state.value.copy(message = error.message ?: "Location lookup failed. You can still use a map pin.")
            } finally {
                mutableState.value = state.value.copy(resolving = false)
            }
        }
    }

    fun save() {
        if (!state.value.loaded || state.value.busy || state.value.phase == PlaceEditorPhase.SAVED) return
        val draft = state.value.draft
        val place = try {
            draft.validate(clock, requestedId ?: id, isHome)
        } catch (error: IllegalArgumentException) {
            mutableState.value = state.value.copy(phase = PlaceEditorPhase.VALIDATION_ERROR, message = error.message.orEmpty())
            return
        }
        mutableState.value = state.value.copy(phase = PlaceEditorPhase.SAVING, message = "")
        viewModelScope.launch {
            try {
                persist(place)
                val warning = register(place)
                savedState.remove<ArrayList<String>>("draft")
                mutableState.value = state.value.copy(
                    phase = PlaceEditorPhase.SAVED,
                    dirty = false,
                    message = if (warning == null) "Place saved." else "Place saved. Geofence warning: $warning",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value =
                    state.value.copy(phase = PlaceEditorPhase.FAILURE, message = error.message ?: "Could not save. Please try again.")
            }
        }
    }

    private suspend fun persist(place: WorkLocation) {
        if (isHome) {
            homeRepository.setHomeLocation(
                HomeLocation(place.latitude, place.longitude, state.value.draft.radius.toFloat(), clock.instant()),
            )
        } else {
            if (requestedId != null) requireNotNull(workRepository.getWorkLocation(requestedId)) { "This saved place no longer exists." }
            require(workRepository.getWorkLocations().none { it.id != place.id && it.projectNumber == place.projectNumber }) {
                "That job number is already assigned to another place."
            }
            workRepository.setWorkLocation(place)
        }
    }

    private suspend fun register(place: WorkLocation): String? = try {
        if (isHome) {
            homeRegistrar.registerHomeGeofence(
                HomeLocation(place.latitude, place.longitude, state.value.draft.radius.toFloat(), clock.instant()),
            )
        } else {
            workRegistrar.registerWorkGeofences(workRepository.getWorkLocations())
        }
        null
    } catch (
        cancelled: CancellationException,
    ) {
        throw cancelled
    } catch (error: Exception) {
        error.message ?: "Registration could not be completed. Check Tracking Setup."
    }
}
