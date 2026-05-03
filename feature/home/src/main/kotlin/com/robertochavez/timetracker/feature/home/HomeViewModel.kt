package com.robertochavez.timetracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.common.repository.HomeLocationRepository
import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeLocationRepository: HomeLocationRepository,
    private val workLocationRepository: WorkLocationRepository,
    private val currentGeofenceLocationProvider: CurrentGeofenceLocationProvider,
    private val homeGeofenceRegistrar: HomeGeofenceRegistrar,
    private val workGeofenceRegistrar: WorkGeofenceRegistrar,
    private val clock: Clock,
    private val logger: AppLogger,
) : ViewModel() {
    private val homeEditorState = MutableStateFlow(HomeEditorState())
    private val workEditorState = MutableStateFlow(WorkEditorState())
    private val statusMessage = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        homeLocationRepository.observeHomeLocation(),
        workLocationRepository.observeWorkLocation(),
        homeEditorState,
        workEditorState,
        statusMessage,
    ) { home, work, homeEditor, workEditor, status ->
        HomeUiState(
            homeSummary = home?.summary() ?: "No home location set",
            workSummary = work?.summary() ?: "No work location set",
            homeLatitude = homeEditor.latitude,
            homeLongitude = homeEditor.longitude,
            homeRadiusMeters = homeEditor.radiusMeters,
            workLatitude = workEditor.latitude,
            workLongitude = workEditor.longitude,
            workRadiusMeters = workEditor.radiusMeters,
            statusMessage = status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun updateHomeField(field: LocationField, value: String) {
        homeEditorState.value = homeEditorState.value.updated(field, value)
    }

    fun updateWorkField(field: LocationField, value: String) {
        workEditorState.value = workEditorState.value.updated(field, value)
    }

    fun useCurrentHomeLocation() {
        logger.info(LogCategory.UI, "Use current home location requested")
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

    fun useCurrentWorkLocation() {
        logger.info(LogCategory.UI, "Use current work location requested")
        viewModelScope.launch {
            runCatching {
                currentGeofenceLocationProvider.currentPreciseWorkLocation(workEditorState.value.radiusInput())
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

    fun saveWorkPin() {
        logger.info(LogCategory.UI, "Save work pin requested")
        viewModelScope.launch {
            workEditorState.value.toWorkLocation(clock).fold(
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
            workGeofenceRegistrar.registerWorkGeofence(workLocation)
        }.fold(
            onSuccess = {
                logger.info(LogCategory.LOCATION, "Work saved and geofence registered")
                "Work location saved and geofence registered."
            },
            onFailure = { error ->
                logger.warn(LogCategory.LOCATION, "Work saved but geofence registration failed", error = error)
                "Work location saved. ${error.message ?: "Geofence could not be registered."}"
            },
        )
    }
}

data class HomeUiState(
    val homeSummary: String = "No home location set",
    val workSummary: String = "No work location set",
    val homeLatitude: String = "",
    val homeLongitude: String = "",
    val homeRadiusMeters: String = HomeLocation.MINIMUM_RADIUS_METERS.toInt().toString(),
    val workLatitude: String = "",
    val workLongitude: String = "",
    val workRadiusMeters: String = WorkLocation.MINIMUM_RADIUS_METERS.toInt().toString(),
    val statusMessage: String = "",
)
