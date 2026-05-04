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

    TimeTrackerScreen(modifier = modifier, testTag = TimeTrackerTestTags.HOME_SCREEN) {
        item {
            TimeTrackerScreenTitle(
                title = "Home",
                subtitle = "Set the places that decide when a work trip starts and stops.",
            )
        }
        item {
            LocationStatusSection(state.homeSummary, state.workSummary)
        }
        item {
            HomeLocationSection(
                state = state,
                onUseCurrentLocation = {
                    requestHomeAction(HomeSaveAction.UseCurrent, state, viewModel) { pendingHomeAction = it }
                },
                onFieldChange = viewModel::updateHomeField,
                onRadiusSelected = viewModel::updateHomeRadius,
                onSave = {
                    requestHomeAction(HomeSaveAction.SavePin, state, viewModel) { pendingHomeAction = it }
                },
            )
        }
        item {
            WorkLocationSection(
                state = state,
                onUseCurrentLocation = {
                    requestWorkAction(WorkSaveAction.UseCurrent, state, viewModel) { pendingWorkAction = it }
                },
                onFieldChange = viewModel::updateWorkField,
                onRadiusSelected = viewModel::updateWorkRadius,
                onSave = {
                    requestWorkAction(WorkSaveAction.SavePin, state, viewModel) { pendingWorkAction = it }
                },
            )
        }
        if (state.statusMessage.isNotBlank()) {
            item {
                TimeTrackerStatusText(state.statusMessage)
            }
        }
    }

    pendingHomeAction?.let { action ->
        PendingHomeActionDialog(action = action, viewModel = viewModel) { pendingHomeAction = null }
    }

    pendingWorkAction?.let { action ->
        PendingWorkActionDialog(action = action, state = state, viewModel = viewModel) { pendingWorkAction = null }
    }
}

@Composable
private fun PendingHomeActionDialog(action: HomeSaveAction, viewModel: HomeViewModel, clearAction: () -> Unit) {
    OverwriteHomeDialog(
        onCancel = clearAction,
        onConfirm = {
            clearAction()
            action.perform(viewModel)
        },
    )
}

@Composable
private fun PendingWorkActionDialog(action: WorkSaveAction, state: HomeUiState, viewModel: HomeViewModel, clearAction: () -> Unit) {
    WorkLocationSaveDialog(
        latestLabel = state.latestWorkLocationLabel.ifBlank { "the latest work site" },
        onCancel = clearAction,
        onAdd = {
            clearAction()
            action.perform(viewModel, replaceLatest = false)
        },
        onReplace = {
            clearAction()
            action.perform(viewModel, replaceLatest = true)
        },
    )
}

private fun requestHomeAction(
    action: HomeSaveAction,
    state: HomeUiState,
    viewModel: HomeViewModel,
    setPendingAction: (HomeSaveAction) -> Unit,
) {
    if (state.homeSet) {
        setPendingAction(action)
    } else {
        action.perform(viewModel)
    }
}

private fun requestWorkAction(
    action: WorkSaveAction,
    state: HomeUiState,
    viewModel: HomeViewModel,
    setPendingAction: (WorkSaveAction) -> Unit,
) {
    if (state.workLocationCount > 0) {
        setPendingAction(action)
    } else {
        action.perform(viewModel, replaceLatest = false)
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
