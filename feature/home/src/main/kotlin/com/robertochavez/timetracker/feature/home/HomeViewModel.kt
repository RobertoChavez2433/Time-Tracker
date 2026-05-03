package com.robertochavez.timetracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.database.repository.HomeLocationRepository
import com.robertochavez.timetracker.core.location.CurrentHomeLocationProvider
import com.robertochavez.timetracker.core.location.geofence.HomeGeofenceRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeLocationRepository: HomeLocationRepository,
    private val currentHomeLocationProvider: CurrentHomeLocationProvider,
    private val homeGeofenceRegistrar: HomeGeofenceRegistrar,
    private val clock: Clock,
) : ViewModel() {
    private val editorState = MutableStateFlow(HomeEditorState())
    private val statusMessage = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        homeLocationRepository.observeHomeLocation(),
        editorState,
        statusMessage,
    ) { home, editor, status ->
        HomeUiState(
            homeSummary = home?.let {
                "${it.latitude.formatCoordinate()}, ${it.longitude.formatCoordinate()} (${it.radiusMeters.toInt()} m)"
            } ?: "No home location set",
            pinLatitude = editor.pinLatitude,
            pinLongitude = editor.pinLongitude,
            pinRadiusMeters = editor.pinRadiusMeters,
            statusMessage = status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun updatePinLatitude(value: String) {
        editorState.value = editorState.value.copy(pinLatitude = value)
    }

    fun updatePinLongitude(value: String) {
        editorState.value = editorState.value.copy(pinLongitude = value)
    }

    fun updatePinRadiusMeters(value: String) {
        editorState.value = editorState.value.copy(pinRadiusMeters = value)
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            runCatching {
                currentHomeLocationProvider.currentPreciseHomeLocation()
            }.onSuccess { home ->
                if (home == null) {
                    statusMessage.value = "Current location was unavailable."
                } else {
                    saveHome(home)
                    editorState.value = HomeEditorState(
                        pinLatitude = home.latitude.toString(),
                        pinLongitude = home.longitude.toString(),
                        pinRadiusMeters = home.radiusMeters.toInt().toString(),
                    )
                    statusMessage.value = "Home location saved from current location."
                }
            }.onFailure { error ->
                statusMessage.value = error.message ?: "Location permission or service unavailable."
            }
        }
    }

    fun saveMapPin() {
        viewModelScope.launch {
            val editor = editorState.value
            val latitude = editor.pinLatitude.toDoubleOrNull()
            val longitude = editor.pinLongitude.toDoubleOrNull()
            val radius = editor.pinRadiusMeters.toFloatOrNull()
            if (latitude == null || longitude == null || radius == null) {
                statusMessage.value = "Enter valid latitude, longitude, and radius."
                return@launch
            }

            runCatching {
                HomeLocation(
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radius,
                    updatedAt = Instant.now(clock),
                )
            }.onSuccess { home ->
                saveHome(home)
                statusMessage.value = "Home pin saved and geofence registered."
            }.onFailure { error ->
                statusMessage.value = error.message ?: "Invalid home pin."
            }
        }
    }

    private suspend fun saveHome(homeLocation: HomeLocation) {
        homeLocationRepository.setHomeLocation(homeLocation)
        homeGeofenceRegistrar.registerHomeGeofence(homeLocation)
    }
}

data class HomeUiState(
    val homeSummary: String = "No home location set",
    val pinLatitude: String = "",
    val pinLongitude: String = "",
    val pinRadiusMeters: String = HomeLocation.MINIMUM_RADIUS_METERS.toInt().toString(),
    val statusMessage: String = "",
)

private data class HomeEditorState(
    val pinLatitude: String = "",
    val pinLongitude: String = "",
    val pinRadiusMeters: String = HomeLocation.MINIMUM_RADIUS_METERS.toInt().toString(),
)

private fun Double.formatCoordinate(): String = "%.5f".format(this)
