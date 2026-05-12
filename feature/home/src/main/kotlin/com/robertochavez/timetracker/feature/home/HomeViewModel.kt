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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@Suppress("LargeClass")
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
                    if (!workEditorState.value.hasCoordinates()) {
                        workEditorState.value = WorkEditorState()
                    }
                } else if (!workEditorState.value.hasCoordinates()) {
                    workEditorState.value = WorkEditorState.fromLocation(work)
                }
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        homeLocationRepository.observeHomeLocation(),
        workLocationRepository.observeWorkLocations(),
        homeEditorState,
        workEditorState,
        statusMessage,
    ) { home, workLocations, homeEditor, workEditor, status ->
        val latestWork = workLocations.firstOrNull()
        HomeUiState(
            homeSummary = home?.summary() ?: "No home location set",
            homeSet = home != null,
            workSummary = workLocations.summary(),
            workLocations = workLocations.map { it.summary() },
            workLocationCount = workLocations.size,
            homeLatitude = homeEditor.latitude,
            homeLongitude = homeEditor.longitude,
            homeRadiusMeters = homeEditor.radiusMeters,
            workLatitude = workEditor.latitude,
            workLongitude = workEditor.longitude,
            workRadiusMeters = workEditor.radiusMeters,
            workLabel = workEditor.label,
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
                currentGeofenceLocationProvider.currentPreciseWorkLocation(workEditorState.value.radiusInput())
                    ?.copy(id = target.id, label = target.label)
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
    val workLocations: List<String> = emptyList(),
    val workLocationCount: Int = 0,
    val homeLatitude: String = "",
    val homeLongitude: String = "",
    val homeRadiusMeters: String = HomeLocation.MINIMUM_RADIUS_METERS.toString(),
    val workLatitude: String = "",
    val workLongitude: String = "",
    val workRadiusMeters: String = WorkLocation.MINIMUM_RADIUS_METERS.toString(),
    val workLabel: String = WorkLocation.DEFAULT_LABEL,
    val latestWorkLocationLabel: String = "",
    val statusMessage: String = "",
)

private data class WorkTarget(val id: String, val label: String)

private fun List<WorkLocation>.summary(): String = when (size) {
    0 -> "No work location set"
    1 -> first().summary()
    else -> "$size work locations saved"
}
