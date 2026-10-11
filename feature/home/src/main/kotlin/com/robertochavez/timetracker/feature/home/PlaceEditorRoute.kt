package com.robertochavez.timetracker.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerDiscardDialog
import com.robertochavez.timetracker.core.designsystem.TimeTrackerEditorActions
import com.robertochavez.timetracker.core.designsystem.TimeTrackerEditorField
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerQuietButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSecondaryButton

@Composable
fun PlaceEditorRoute(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: PlaceEditorViewModel = hiltViewModel(),
    disclosure: MapDisclosureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accepted by disclosure.accepted.collectAsStateWithLifecycle()
    var advanced by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var map by rememberSaveable { mutableStateOf(false) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.phase) { if (state.phase == PlaceEditorPhase.SAVED) onSaved(state.message) }
    BackHandler(enabled = !map && !showDisclosure) {
        when {
            state.phase == PlaceEditorPhase.SAVING -> Unit
            state.dirty -> discard = true
            else -> onBack()
        }
    }
    PlaceEditorScreen(
        state,
        viewModel,
        advanced,
        { advanced = !advanced },
        { if (accepted) map = true else showDisclosure = true },
        onBack,
    )

    if (discard) TimeTrackerDiscardDialog({ discard = false }, onBack)
    if (showDisclosure) {
        MapNetworkDisclosure(
            onAccept = {
                disclosure.accept()
                showDisclosure = false
                map = true
            },
            onDecline = {
                disclosure.decline()
                showDisclosure = false
            },
        )
    }
    if (map) {
        val confirm = { lat: Double, lon: Double, radius: Float ->
            viewModel.selectMapPin(lat, lon, radius)
            map = false
        }
        PlaceEditorMap(state, { map = false }, confirm)
    }
}

@Composable
private fun PlaceEditorMap(state: PlaceEditorState, onCancel: () -> Unit, confirm: (Double, Double, Float) -> Unit) {
    if (state.isHome) {
        HomeMapPicker(
            state.draft.latitude.toDoubleOrNull(),
            state.draft.longitude.toDoubleOrNull(),
            state.draft.radius.toFloat(),
            state.references,
            onCancel,
            confirm,
        )
    } else {
        WorksiteMapPicker(
            state.draft.latitude.toDoubleOrNull(),
            state.draft.longitude.toDoubleOrNull(),
            state.draft.radius.toFloat(),
            state.references,
            onCancel,
            confirm,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.placeEditorFields(
    state: PlaceEditorState,
    viewModel: PlaceEditorViewModel,
    advanced: Boolean,
    onAdvanced: () -> Unit,
    onMap: () -> Unit,
    onBack: () -> Unit,
) {
    item { PlaceIdentityFields(state, viewModel) }
    item { PlaceLocationFields(state, viewModel, onMap) }
    item { PlaceRadius(state, viewModel) }
    item { PlaceCoordinates(state, viewModel, advanced, onAdvanced) }
    if (state.message.isNotBlank()) item { PlaceMessage(state) }
    item { TimeTrackerEditorActions(viewModel::save, onBack, state.phase == PlaceEditorPhase.SAVING, !state.busy) }
}

@Composable
private fun PlaceIdentityFields(state: PlaceEditorState, viewModel: PlaceEditorViewModel) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeTrackerEditorField(
            "Name",
            state.draft.name,
            { viewModel.update(LocationField.LABEL, it) },
            "place_name",
            !state.busy && !state.isHome,
        )
        if (!state.isHome) {
            TimeTrackerEditorField(
                "Job number",
                state.draft.job,
                { viewModel.update(LocationField.PROJECT_NUMBER, it) },
                "place_job",
                !state.busy,
            )
        }
    }
}

@Composable
private fun PlaceLocationFields(state: PlaceEditorState, viewModel: PlaceEditorViewModel, onMap: () -> Unit) {
    androidx.compose.foundation.layout.Column {
        TimeTrackerEditorField(
            "Address / location",
            state.draft.address,
            { viewModel.update(LocationField.ADDRESS, it) },
            "place_address",
            !state.busy,
        )
        TimeTrackerQuietButton(
            "Find address",
            viewModel::resolveAddress,
            Modifier.testTag("place_find_address"),
            !state.busy && state.draft.address.isNotBlank(),
        )
        if (state.draft.latitude.isNotBlank()) TimeTrackerMutedText("Location pin selected. Use the map to review it.")
        TimeTrackerSecondaryButton("Choose on map", onMap, Modifier.fillMaxWidth().testTag("place_map"), !state.busy)
        TimeTrackerQuietButton(
            "Use current location",
            viewModel::useCurrentLocation,
            Modifier.testTag("place_current_location"),
            !state.busy,
        )
    }
}

@Composable
private fun PlaceRadius(state: PlaceEditorState, viewModel: PlaceEditorViewModel) {
    androidx.compose.foundation.layout.Column {
        if (state.isHome) {
            RadiusSelector("Home radius", state.draft.radius, "home") {
                viewModel.update(LocationField.RADIUS_METERS, it.toString())
            }
        } else {
            WorkRadiusSlider(state.draft.radius) { viewModel.update(LocationField.RADIUS_METERS, it.toString()) }
        }
    }
}

@Composable
private fun PlaceCoordinates(state: PlaceEditorState, viewModel: PlaceEditorViewModel, advanced: Boolean, onAdvanced: () -> Unit) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeTrackerQuietButton(if (advanced) "Hide coordinates" else "Advanced coordinates", onAdvanced, Modifier.testTag("place_advanced"))
        if (advanced) {
            TimeTrackerEditorField("Latitude", state.draft.latitude, {
                viewModel.update(LocationField.LATITUDE, it)
            }, "place_latitude", !state.busy)
            TimeTrackerEditorField("Longitude", state.draft.longitude, {
                viewModel.update(LocationField.LONGITUDE, it)
            }, "place_longitude", !state.busy)
        }
    }
}

@Composable
private fun PlaceMessage(state: PlaceEditorState) {
    val failed = state.phase == PlaceEditorPhase.FAILURE || state.phase == PlaceEditorPhase.VALIDATION_ERROR
    Text(state.message, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun PlaceEditorScreen(
    state: PlaceEditorState,
    viewModel: PlaceEditorViewModel,
    advanced: Boolean,
    onAdvanced: () -> Unit,
    onMap: () -> Unit,
    onBack: () -> Unit,
) {
    TimeTrackerScreen(Modifier.imePadding(), "place_editor_screen") {
        item { TimeTrackerScreenTitle(if (state.isNew) "Add Place" else "Edit Place", if (state.isHome) "Home location" else null) }
        if (state.phase == PlaceEditorPhase.LOADING) {
            item { Text("Loading…") }
        } else if (!state.loaded) {
            item { Text(state.message, color = MaterialTheme.colorScheme.error) }
            item { TimeTrackerQuietButton("Retry", viewModel::load) }
            item { TimeTrackerQuietButton("Cancel", onBack) }
        } else {
            placeEditorFields(
                state,
                viewModel,
                advanced,
                onAdvanced,
                onMap,
                onBack,
            )
        }
    }
}
