package com.robertochavez.timetracker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.robertochavez.timetracker.core.common.model.GeofenceRadiusOptions
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerQuietButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSecondaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSettingRow
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSettingSection
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@Composable
internal fun LocationStatusSection(homeSummary: String, workSummary: String) {
    TimeTrackerSettingSection(title = "Setup status") {
        TimeTrackerMutedText("Home: $homeSummary")
        TimeTrackerMutedText("Work: $workSummary")
    }
}

@Composable
internal fun HomeLocationSection(state: HomeUiState, editingPin: Boolean, actions: LocationSectionActions) {
    TimeTrackerSettingSection(title = "Home", subtitle = state.homeSummary) {
        if (editingPin) {
            CoordinateRow(
                latitude = state.homeLatitude,
                longitude = state.homeLongitude,
                onLatitudeChange = { actions.onFieldChange(LocationField.LATITUDE, it) },
                onLongitudeChange = { actions.onFieldChange(LocationField.LONGITUDE, it) },
                latitudeTag = TimeTrackerTestTags.HOME_LATITUDE_FIELD,
                longitudeTag = TimeTrackerTestTags.HOME_LONGITUDE_FIELD,
            )
            RadiusSelector(
                title = "Home radius",
                selectedRadiusMeters = state.homeRadiusMeters,
                tagPrefix = "home",
                onRadiusSelected = actions.onRadiusSelected,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeTrackerQuietButton(
                    text = "Cancel",
                    onClick = actions.onCancelEdit,
                    modifier = Modifier.testTag(TimeTrackerTestTags.HOME_CANCEL_EDIT_BUTTON),
                )
                TimeTrackerPrimaryButton(
                    text = "Save Pin",
                    onClick = actions.onSave,
                    modifier = Modifier.testTag(TimeTrackerTestTags.HOME_SAVE_PIN_BUTTON),
                )
            }
        } else {
            RadiusSelector(
                title = "Home radius",
                selectedRadiusMeters = state.homeRadiusMeters,
                tagPrefix = "home",
                onRadiusSelected = actions.onRadiusSelected,
            )
            TimeTrackerPrimaryButton(
                text = "Use Current Location",
                onClick = actions.onUseCurrentLocation,
                modifier = Modifier.testTag(TimeTrackerTestTags.HOME_USE_CURRENT_BUTTON),
            )
            if (state.homeSet) {
                TimeTrackerSecondaryButton(
                    text = "Save Radius",
                    onClick = actions.onSaveRadius,
                    modifier = Modifier.testTag(TimeTrackerTestTags.HOME_SAVE_RADIUS_BUTTON),
                )
            }
            TimeTrackerSecondaryButton(
                text = "Edit Pin",
                onClick = actions.onEditPin,
                modifier = Modifier.testTag(TimeTrackerTestTags.HOME_EDIT_PIN_BUTTON),
            )
        }
    }
}

@Composable
internal fun WorkLocationSection(state: HomeUiState, editingPin: Boolean, actions: LocationSectionActions) {
    TimeTrackerSettingSection(title = "Work sites", subtitle = "Job-site driving stays out of commute totals.") {
        if (editingPin) {
            WorkPinEditor(state, actions)
        } else {
            WorkLocationSummaryList(state.workLocations)
            WorkLocationActions(state, actions)
        }
    }
}

internal data class LocationSectionActions(
    val onUseCurrentLocation: () -> Unit,
    val onEditPin: () -> Unit,
    val onCancelEdit: () -> Unit,
    val onFieldChange: (LocationField, String) -> Unit,
    val onRadiusSelected: (Float) -> Unit,
    val onSaveRadius: () -> Unit,
    val onSave: () -> Unit,
)

@Composable
internal fun CoordinateRow(
    latitude: String,
    longitude: String,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    latitudeTag: String,
    longitudeTag: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PinNumberField(
            label = "Latitude",
            value = latitude,
            onValueChange = onLatitudeChange,
            modifier = Modifier.weight(1f),
            testTag = latitudeTag,
            keyboardType = KeyboardType.Text,
        )
        PinNumberField(
            label = "Longitude",
            value = longitude,
            onValueChange = onLongitudeChange,
            modifier = Modifier.weight(1f),
            testTag = longitudeTag,
            keyboardType = KeyboardType.Text,
        )
    }
}

@Composable
internal fun RadiusSelector(title: String, selectedRadiusMeters: String, tagPrefix: String, onRadiusSelected: (Float) -> Unit) {
    val selected = GeofenceRadiusOptions.nearest(
        selectedRadiusMeters.toFloatOrNull() ?: GeofenceRadiusOptions.default.meters,
    )
    var expanded by rememberSaveable { mutableStateOf(false) }
    TimeTrackerSettingRow(label = title, supportingText = "Selected: ${selected.label}") {
        Box {
            TimeTrackerSecondaryButton(
                text = selected.label,
                onClick = { expanded = true },
                modifier = Modifier.testTag("${tagPrefix}_radius_dropdown"),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                GeofenceRadiusOptions.all.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            onRadiusSelected(option.meters)
                        },
                        modifier = Modifier.testTag(TimeTrackerTestTags.radiusOption(tagPrefix, option.testTagSuffix)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PinNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = modifier.testTag(testTag),
    )
}
