package com.robertochavez.timetracker.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.common.model.UiDebugState
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerStatusText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

private enum class MapTarget {
    HOME,
    WORK,
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun HomeRoute(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    mapDisclosureViewModel: MapDisclosureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mapDisclosureAccepted by mapDisclosureViewModel.accepted.collectAsStateWithLifecycle()
    var pendingHomeAction by rememberSaveable { mutableStateOf<HomeSaveAction?>(null) }
    var homeEditMode by rememberSaveable { mutableStateOf(HomeEditMode.CLOSED) }
    var editingWorkPin by rememberSaveable { mutableStateOf(false) }
    var addingPlace by rememberSaveable { mutableStateOf(false) }
    var showMapDisclosure by rememberSaveable { mutableStateOf(false) }
    var mapDisclosureTarget by rememberSaveable { mutableStateOf<MapTarget?>(null) }
    var showHomeMap by rememberSaveable { mutableStateOf(false) }
    var showWorksiteMap by rememberSaveable { mutableStateOf(false) }
    var homeMapLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var homeMapLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var worksiteMapLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var worksiteMapLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    val openHomeMapAtCurrentLocation = {
        viewModel.prepareHomeMapFromCurrentLocation { latitude, longitude ->
            homeMapLatitude = latitude
            homeMapLongitude = longitude
            showHomeMap = true
        }
    }
    val openWorksiteMapAtCurrentLocation = {
        viewModel.prepareWorkMapFromCurrentLocation { latitude, longitude ->
            worksiteMapLatitude = latitude
            worksiteMapLongitude = longitude
            showWorksiteMap = true
        }
    }
    LaunchedEffect(showMapDisclosure, showHomeMap, showWorksiteMap, pendingHomeAction) {
        UiDebugState.setModal(
            when {
                showMapDisclosure -> "map_disclosure"
                showHomeMap -> "home_map"
                showWorksiteMap -> "worksite_map"
                pendingHomeAction != null -> "home_overwrite"
                else -> null
            },
        )
    }

    val homeActions = LocationSectionActions(
        onUseCurrentLocation = {
            requestHomeAction(
                action = HomeSaveAction.UseCurrent,
                state = state,
                viewModel = viewModel,
                setPendingAction = { pendingHomeAction = it },
                onPerformed = { homeEditMode = HomeEditMode.CLOSED },
            )
        },
        onEditPin = { homeEditMode = HomeEditMode.ADDRESS },
        onCancelEdit = {
            viewModel.cancelHomeEdit()
            homeEditMode = HomeEditMode.CLOSED
        },
        onFieldChange = viewModel::updateHomeField,
        onRadiusSelected = viewModel::updateHomeRadius,
        onSaveRadius = viewModel::saveHomePin,
        onResolveAddress = viewModel::resolveHomeAddress,
        onOpenMap = {
            if (mapDisclosureAccepted) {
                openHomeMapAtCurrentLocation()
            } else {
                mapDisclosureTarget = MapTarget.HOME
                showMapDisclosure = true
            }
        },
        onSave = {
            requestHomeAction(
                action = HomeSaveAction.SavePin,
                state = state,
                viewModel = viewModel,
                setPendingAction = { pendingHomeAction = it },
                onPerformed = { homeEditMode = HomeEditMode.CLOSED },
            )
        },
    )
    val workActions = LocationSectionActions(
        onUseCurrentLocation = {
            viewModel.useCurrentWorkLocation()
            editingWorkPin = false
            addingPlace = false
        },
        onEditPin = { editingWorkPin = true },
        onCancelEdit = { editingWorkPin = false },
        onFieldChange = viewModel::updateWorkField,
        onRadiusSelected = viewModel::updateWorkRadius,
        onSaveRadius = viewModel::saveWorkPin,
        onResolveAddress = viewModel::resolveWorkAddress,
        onOpenMap = {
            addingPlace = true
            editingWorkPin = true
            if (mapDisclosureAccepted) {
                openWorksiteMapAtCurrentLocation()
            } else {
                mapDisclosureTarget = MapTarget.WORK
                showMapDisclosure = true
            }
        },
        onSave = {
            viewModel.saveWorkPin()
            editingWorkPin = false
            addingPlace = false
        },
    )

    PlacesScreen(
        modifier = modifier,
        state = state,
        homeEditMode = homeEditMode,
        editingWorkPin = editingWorkPin,
        addingPlace = addingPlace,
        homeActions = homeActions,
        workActions = workActions,
        onEditHome = {
            viewModel.cancelHomeEdit()
            homeEditMode = HomeEditMode.METHODS
        },
        onAddPlace = {
            viewModel.beginAddPlace()
            addingPlace = true
            editingWorkPin = false
        },
        onEditPlace = { id ->
            viewModel.beginEditPlace(id)
            addingPlace = true
            editingWorkPin = true
        },
        onCloseAddPlace = {
            addingPlace = false
            editingWorkPin = false
        },
    )

    pendingHomeAction?.let { action ->
        PendingHomeActionDialog(action = action, viewModel = viewModel, onPinSaved = { homeEditMode = HomeEditMode.CLOSED }) {
            pendingHomeAction = null
        }
    }

    if (showMapDisclosure) {
        MapNetworkDisclosure(
            onAccept = {
                mapDisclosureViewModel.accept()
                showMapDisclosure = false
                when (mapDisclosureTarget) {
                    MapTarget.HOME -> openHomeMapAtCurrentLocation()
                    MapTarget.WORK -> openWorksiteMapAtCurrentLocation()
                    null -> Unit
                }
                mapDisclosureTarget = null
            },
            onDecline = {
                mapDisclosureViewModel.decline()
                showMapDisclosure = false
                mapDisclosureTarget = null
                showHomeMap = false
                showWorksiteMap = false
            },
        )
    }

    if (showHomeMap) {
        HomeMapPicker(
            initialLatitude = homeMapLatitude ?: state.homeLatitude.toDoubleOrNull(),
            initialLongitude = homeMapLongitude ?: state.homeLongitude.toDoubleOrNull(),
            initialRadiusMeters = state.homeRadiusMeters.toFloatOrNull()
                ?: com.robertochavez.timetracker.core.common.model.HomeLocation.MINIMUM_RADIUS_METERS,
            referenceLocations = state.mapReferenceLocations,
            onCancel = {
                showHomeMap = false
                homeMapLatitude = null
                homeMapLongitude = null
            },
            onConfirm = { latitude, longitude, radiusMeters ->
                viewModel.selectHomeMapPin(latitude, longitude, radiusMeters)
                showHomeMap = false
                homeMapLatitude = null
                homeMapLongitude = null
                homeEditMode = HomeEditMode.ADDRESS
            },
        )
    }

    if (showWorksiteMap) {
        WorksiteMapPicker(
            initialLatitude = worksiteMapLatitude ?: state.workLatitude.toDoubleOrNull(),
            initialLongitude = worksiteMapLongitude ?: state.workLongitude.toDoubleOrNull(),
            initialRadiusMeters = state.workRadiusMeters.toFloatOrNull()
                ?: com.robertochavez.timetracker.core.common.model.WorkLocation.MINIMUM_RADIUS_METERS,
            referenceLocations = state.mapReferenceLocations,
            onCancel = {
                showWorksiteMap = false
                worksiteMapLatitude = null
                worksiteMapLongitude = null
            },
            onConfirm = { latitude, longitude, radiusMeters ->
                viewModel.selectWorkMapPin(latitude, longitude, radiusMeters)
                showWorksiteMap = false
                worksiteMapLatitude = null
                worksiteMapLongitude = null
                editingWorkPin = true
                addingPlace = true
            },
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun PlacesScreen(
    modifier: Modifier,
    state: HomeUiState,
    homeEditMode: HomeEditMode,
    editingWorkPin: Boolean,
    addingPlace: Boolean,
    homeActions: LocationSectionActions,
    workActions: LocationSectionActions,
    onEditHome: () -> Unit,
    onAddPlace: () -> Unit,
    onEditPlace: (String) -> Unit,
    onCloseAddPlace: () -> Unit,
) {
    TimeTrackerScreen(modifier = modifier, testTag = TimeTrackerTestTags.HOME_SCREEN) {
        item {
            TimeTrackerScreenTitle(
                title = "Places",
                subtitle = "Manage the locations used for automatic tracking.",
            )
        }
        item {
            SavedPlacesSection(state.workLocations, onEditPlace)
        }
        item {
            HomeLocationSection(
                state = state,
                editMode = homeEditMode,
                actions = homeActions,
                onStartEdit = onEditHome,
            )
        }
        item {
            if (addingPlace) {
                AddPlaceSection(
                    state = state,
                    editingPin = editingWorkPin,
                    actions = workActions,
                    onClose = onCloseAddPlace,
                )
            } else {
                AddPlaceButton(onClick = onAddPlace)
            }
        }
        if (state.statusMessage.isNotBlank()) {
            item {
                TimeTrackerStatusText(state.statusMessage)
            }
        }
    }
}

@Composable
private fun PendingHomeActionDialog(action: HomeSaveAction, viewModel: HomeViewModel, onPinSaved: () -> Unit, clearAction: () -> Unit) {
    OverwriteHomeDialog(
        onCancel = clearAction,
        onConfirm = {
            clearAction()
            action.perform(viewModel)
            onPinSaved()
        },
    )
}

private fun requestHomeAction(
    action: HomeSaveAction,
    state: HomeUiState,
    viewModel: HomeViewModel,
    setPendingAction: (HomeSaveAction) -> Unit,
    onPerformed: () -> Unit,
) {
    if (state.homeSet) {
        setPendingAction(action)
    } else {
        action.perform(viewModel)
        onPerformed()
    }
}

private fun HomeSaveAction.perform(viewModel: HomeViewModel) {
    when (this) {
        HomeSaveAction.UseCurrent -> viewModel.useCurrentHomeLocation()
        HomeSaveAction.SavePin -> viewModel.saveHomePin()
    }
}
