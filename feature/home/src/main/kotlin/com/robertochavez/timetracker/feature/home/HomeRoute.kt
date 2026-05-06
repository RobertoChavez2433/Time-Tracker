package com.robertochavez.timetracker.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerStatusText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@Composable
fun HomeRoute(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingHomeAction by rememberSaveable { mutableStateOf<HomeSaveAction?>(null) }
    var pendingWorkAction by rememberSaveable { mutableStateOf<WorkSaveAction?>(null) }
    var editingHomePin by rememberSaveable { mutableStateOf(false) }
    var editingWorkPin by rememberSaveable { mutableStateOf(false) }

    val homeActions = LocationSectionActions(
        onUseCurrentLocation = {
            requestHomeAction(
                action = HomeSaveAction.UseCurrent,
                state = state,
                viewModel = viewModel,
                setPendingAction = { pendingHomeAction = it },
                onPerformed = { editingHomePin = false },
            )
        },
        onEditPin = { editingHomePin = true },
        onCancelEdit = { editingHomePin = false },
        onFieldChange = viewModel::updateHomeField,
        onRadiusSelected = viewModel::updateHomeRadius,
        onSaveRadius = viewModel::saveHomePin,
        onSave = {
            requestHomeAction(
                action = HomeSaveAction.SavePin,
                state = state,
                viewModel = viewModel,
                setPendingAction = { pendingHomeAction = it },
                onPerformed = { editingHomePin = false },
            )
        },
    )
    val workActions = LocationSectionActions(
        onUseCurrentLocation = {
            requestWorkAction(
                action = WorkSaveAction.UseCurrent,
                state = state,
                viewModel = viewModel,
                setPendingAction = { pendingWorkAction = it },
                onPerformed = { editingWorkPin = false },
            )
        },
        onEditPin = { editingWorkPin = true },
        onCancelEdit = { editingWorkPin = false },
        onFieldChange = viewModel::updateWorkField,
        onRadiusSelected = viewModel::updateWorkRadius,
        onSaveRadius = { viewModel.saveWorkPin(replaceLatest = true) },
        onSave = {
            requestWorkAction(
                action = WorkSaveAction.SavePin,
                state = state,
                viewModel = viewModel,
                setPendingAction = { pendingWorkAction = it },
                onPerformed = { editingWorkPin = false },
            )
        },
    )

    PlacesScreen(
        modifier = modifier,
        state = state,
        editingHomePin = editingHomePin,
        editingWorkPin = editingWorkPin,
        homeActions = homeActions,
        workActions = workActions,
    )

    pendingHomeAction?.let { action ->
        PendingHomeActionDialog(action = action, viewModel = viewModel, onPinSaved = { editingHomePin = false }) {
            pendingHomeAction = null
        }
    }

    pendingWorkAction?.let { action ->
        PendingWorkActionDialog(
            action = action,
            state = state,
            viewModel = viewModel,
            onPinSaved = { editingWorkPin = false },
        ) {
            pendingWorkAction = null
        }
    }
}

@Composable
private fun PlacesScreen(
    modifier: Modifier,
    state: HomeUiState,
    editingHomePin: Boolean,
    editingWorkPin: Boolean,
    homeActions: LocationSectionActions,
    workActions: LocationSectionActions,
) {
    TimeTrackerScreen(modifier = modifier, testTag = TimeTrackerTestTags.HOME_SCREEN) {
        item {
            TimeTrackerScreenTitle(
                title = "Places",
                subtitle = "Set the home and work sites used for automatic tracking.",
            )
        }
        item {
            LocationStatusSection(state.homeSummary, state.workSummary)
        }
        item {
            HomeLocationSection(state = state, editingPin = editingHomePin, actions = homeActions)
        }
        item {
            WorkLocationSection(state = state, editingPin = editingWorkPin, actions = workActions)
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

@Composable
private fun PendingWorkActionDialog(
    action: WorkSaveAction,
    state: HomeUiState,
    viewModel: HomeViewModel,
    onPinSaved: () -> Unit,
    clearAction: () -> Unit,
) {
    WorkLocationSaveDialog(
        latestLabel = state.latestWorkLocationLabel.ifBlank { "the latest work site" },
        onCancel = clearAction,
        onAdd = {
            clearAction()
            action.perform(viewModel, replaceLatest = false)
            onPinSaved()
        },
        onReplace = {
            clearAction()
            action.perform(viewModel, replaceLatest = true)
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

private fun requestWorkAction(
    action: WorkSaveAction,
    state: HomeUiState,
    viewModel: HomeViewModel,
    setPendingAction: (WorkSaveAction) -> Unit,
    onPerformed: () -> Unit,
) {
    if (state.workLocationCount > 0) {
        setPendingAction(action)
    } else {
        action.perform(viewModel, replaceLatest = false)
        onPerformed()
    }
}

private fun HomeSaveAction.perform(viewModel: HomeViewModel) {
    when (this) {
        HomeSaveAction.UseCurrent -> viewModel.useCurrentHomeLocation()
        HomeSaveAction.SavePin -> viewModel.saveHomePin()
    }
}

private fun WorkSaveAction.perform(viewModel: HomeViewModel, replaceLatest: Boolean) {
    when (this) {
        WorkSaveAction.UseCurrent -> viewModel.useCurrentWorkLocation(replaceLatest)
        WorkSaveAction.SavePin -> viewModel.saveWorkPin(replaceLatest)
    }
}
