package com.robertochavez.timetracker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
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
internal fun WorkLocationSummaryList(workLocations: List<String>) {
    workLocations.forEach { summary -> TimeTrackerMutedText(summary) }
    if (workLocations.isEmpty()) TimeTrackerMutedText("No work location set")
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
    RadiusSelector(
        title = "Work radius",
        selectedRadiusMeters = state.workRadiusMeters,
        tagPrefix = "work",
        onRadiusSelected = actions.onRadiusSelected,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    RadiusSelector(
        title = if (state.workLocationCount > 0) "Latest work radius" else "Work radius",
        selectedRadiusMeters = state.workRadiusMeters,
        tagPrefix = "work",
        onRadiusSelected = actions.onRadiusSelected,
    )
    TimeTrackerPrimaryButton(
        text = "Use Current Location",
        onClick = actions.onUseCurrentLocation,
        modifier = Modifier.testTag(TimeTrackerTestTags.WORK_USE_CURRENT_BUTTON),
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
