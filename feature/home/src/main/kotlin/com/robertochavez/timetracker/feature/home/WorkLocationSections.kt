package com.robertochavez.timetracker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerQuietButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSecondaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@Composable
internal fun SavedPlacesSection(workLocations: List<SavedPlaceUiModel>) {
    com.robertochavez.timetracker.core.designsystem.TimeTrackerSettingSection(
        title = "Saved places",
        subtitle = "Work sites and other places used for automatic tracking.",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TimeTrackerTestTags.PLACES_SAVED_LIST),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            workLocations.forEach { place ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TimeTrackerTestTags.savedPlaceItem(place.id)),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(place.label, style = MaterialTheme.typography.titleSmall)
                        TimeTrackerMutedText(place.address ?: "Address not saved")
                        TimeTrackerMutedText("${place.radius} radius")
                    }
                }
            }
            if (workLocations.isEmpty()) TimeTrackerMutedText("No saved places yet")
        }
    }
}

@Composable
internal fun AddPlaceButton(onClick: () -> Unit) {
    TimeTrackerPrimaryButton(
        text = "Add new work site or place",
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TimeTrackerTestTags.ADD_PLACE_BUTTON),
    )
}

@Composable
internal fun AddPlaceSection(state: HomeUiState, editingPin: Boolean, actions: LocationSectionActions, onClose: () -> Unit) {
    com.robertochavez.timetracker.core.designsystem.TimeTrackerSettingSection(
        title = "Add new work site or place",
        subtitle = "Choose the quickest way to locate this place.",
        modifier = Modifier.testTag(TimeTrackerTestTags.ADD_PLACE_FLOW),
    ) {
        if (editingPin) {
            WorkPinEditor(state, actions)
        } else {
            TimeTrackerPrimaryButton(
                text = "Use the current location",
                onClick = actions.onUseCurrentLocation,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TimeTrackerTestTags.ADD_PLACE_CURRENT_BUTTON),
            )
            TimeTrackerSecondaryButton(
                text = "Drop a pin on the map",
                onClick = actions.onOpenMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TimeTrackerTestTags.ADD_PLACE_MAP_BUTTON),
            )
            TimeTrackerSecondaryButton(
                text = "Add by address",
                onClick = actions.onEditPin,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TimeTrackerTestTags.ADD_PLACE_ADDRESS_BUTTON),
            )
            TimeTrackerQuietButton(text = "Cancel", onClick = onClose)
        }
    }
}

@Composable
internal fun WorkPinEditor(state: HomeUiState, actions: LocationSectionActions) {
    OutlinedTextField(
        value = state.workLabel,
        onValueChange = { actions.onFieldChange(LocationField.LABEL, it) },
        label = { Text("Site name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(TimeTrackerTestTags.WORK_LABEL_FIELD),
    )
    OutlinedTextField(
        value = state.workAddress,
        onValueChange = { actions.onFieldChange(LocationField.ADDRESS, it) },
        label = { Text("Address") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(TimeTrackerTestTags.WORK_ADDRESS_FIELD),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeTrackerSecondaryButton(
            text = "Find Address",
            onClick = actions.onResolveAddress,
            modifier = Modifier.testTag(TimeTrackerTestTags.WORK_RESOLVE_ADDRESS_BUTTON),
        )
        if (state.workResolvedAddress.isNotBlank()) {
            TimeTrackerMutedText("Resolved: ${state.workResolvedAddress}")
        }
    }
    CoordinateRow(
        latitude = state.workLatitude,
        longitude = state.workLongitude,
        onLatitudeChange = { actions.onFieldChange(LocationField.LATITUDE, it) },
        onLongitudeChange = { actions.onFieldChange(LocationField.LONGITUDE, it) },
        latitudeTag = TimeTrackerTestTags.WORK_LATITUDE_FIELD,
        longitudeTag = TimeTrackerTestTags.WORK_LONGITUDE_FIELD,
    )
    WorkRadiusSlider(
        selectedRadiusMeters = state.workRadiusMeters,
        onRadiusSelected = actions.onRadiusSelected,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeTrackerSecondaryButton(
            text = "Drop Pin on Map",
            onClick = actions.onOpenMap,
            modifier = Modifier.testTag(TimeTrackerTestTags.WORK_OPEN_MAP_BUTTON),
        )
        TimeTrackerQuietButton(
            text = "Cancel",
            onClick = actions.onCancelEdit,
            modifier = Modifier.testTag(TimeTrackerTestTags.WORK_CANCEL_EDIT_BUTTON),
        )
        TimeTrackerSecondaryButton(
            text = "Save Pin",
            onClick = actions.onSave,
            modifier = Modifier.testTag(TimeTrackerTestTags.WORK_SAVE_PIN_BUTTON),
        )
    }
}

@Composable
internal fun WorkLocationActions(state: HomeUiState, actions: LocationSectionActions) {
    WorkRadiusSlider(
        selectedRadiusMeters = state.workRadiusMeters,
        onRadiusSelected = actions.onRadiusSelected,
    )
    TimeTrackerPrimaryButton(
        text = "Use Current Location",
        onClick = actions.onUseCurrentLocation,
        modifier = Modifier.testTag(TimeTrackerTestTags.WORK_USE_CURRENT_BUTTON),
    )
    TimeTrackerSecondaryButton(
        text = "Drop Pin on Map",
        onClick = actions.onOpenMap,
        modifier = Modifier.testTag(TimeTrackerTestTags.WORK_OPEN_MAP_BUTTON),
    )
    if (state.workLocationCount > 0) {
        TimeTrackerSecondaryButton(
            text = "Save Radius",
            onClick = actions.onSaveRadius,
            modifier = Modifier.testTag(TimeTrackerTestTags.WORK_SAVE_RADIUS_BUTTON),
        )
    }
    TimeTrackerSecondaryButton(
        text = if (state.workLocationCount > 0) "Add / Edit Site" else "Add Manual Site",
        onClick = actions.onEditPin,
        modifier = Modifier.testTag(TimeTrackerTestTags.WORK_EDIT_PIN_BUTTON),
    )
}
