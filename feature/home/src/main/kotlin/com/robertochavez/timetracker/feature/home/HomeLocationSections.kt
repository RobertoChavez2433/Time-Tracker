package com.robertochavez.timetracker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerQuietButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSecondaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSettingRow
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSettingSection
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags
import kotlin.math.roundToInt

@Composable
internal fun LocationStatusSection(homeSummary: String, workSummary: String) {
    TimeTrackerSettingSection(title = "Setup status") {
        TimeTrackerMutedText("Home: $homeSummary")
        TimeTrackerMutedText("Work: $workSummary")
    }
}

internal enum class HomeEditMode {
    CLOSED,
    METHODS,
    ADDRESS,
}

@Composable
internal fun HomeLocationSection(state: HomeUiState, editMode: HomeEditMode, actions: LocationSectionActions, onStartEdit: () -> Unit) {
    TimeTrackerSettingSection(title = "Home location", subtitle = state.homeSummary) {
        when (editMode) {
            HomeEditMode.CLOSED -> {
                val radius = GeofenceRadiusOptions.nearest(
                    state.homeRadiusMeters.toFloatOrNull() ?: GeofenceRadiusOptions.default.meters,
                )
                TimeTrackerMutedText("Home radius: ${radius.label}")
                TimeTrackerPrimaryButton(
                    text = "Edit Pin and Radius",
                    onClick = onStartEdit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TimeTrackerTestTags.HOME_EDIT_PIN_BUTTON),
                )
            }

            HomeEditMode.METHODS -> HomeLocationMethods(state, actions)
            HomeEditMode.ADDRESS -> HomePinEditor(state, actions)
        }
    }
}

@Composable
private fun HomeLocationMethods(state: HomeUiState, actions: LocationSectionActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TimeTrackerTestTags.HOME_EDIT_FLOW),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadiusSelector(
            title = "Home radius",
            selectedRadiusMeters = state.homeRadiusMeters,
            tagPrefix = "home",
            onRadiusSelected = actions.onRadiusSelected,
        )
        TimeTrackerPrimaryButton(
            text = "Use the current location",
            onClick = actions.onUseCurrentLocation,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TimeTrackerTestTags.HOME_EDIT_CURRENT_BUTTON),
        )
        TimeTrackerSecondaryButton(
            text = "Drop a pin on the map",
            onClick = actions.onOpenMap,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TimeTrackerTestTags.HOME_EDIT_MAP_BUTTON),
        )
        TimeTrackerSecondaryButton(
            text = "Add by address",
            onClick = actions.onEditPin,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TimeTrackerTestTags.HOME_EDIT_ADDRESS_BUTTON),
        )
        TimeTrackerQuietButton(
            text = "Cancel",
            onClick = actions.onCancelEdit,
            modifier = Modifier.testTag(TimeTrackerTestTags.HOME_CANCEL_EDIT_BUTTON),
        )
    }
}

@Composable
private fun HomePinEditor(state: HomeUiState, actions: LocationSectionActions) {
    OutlinedTextField(
        value = state.homeAddress,
        onValueChange = { actions.onFieldChange(LocationField.ADDRESS, it) },
        label = { Text("Home address") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TimeTrackerTestTags.HOME_ADDRESS_FIELD),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeTrackerSecondaryButton(
            text = "Find Address",
            onClick = actions.onResolveAddress,
            modifier = Modifier.testTag(TimeTrackerTestTags.HOME_RESOLVE_ADDRESS_BUTTON),
        )
        if (state.homeResolvedAddress.isNotBlank()) {
            TimeTrackerMutedText("Resolved: ${state.homeResolvedAddress}")
        }
    }
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
    TimeTrackerSecondaryButton(
        text = "Drop Pin on Map",
        onClick = actions.onOpenMap,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TimeTrackerTestTags.HOME_OPEN_MAP_BUTTON),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeTrackerQuietButton(
            text = "Cancel",
            onClick = actions.onCancelEdit,
            modifier = Modifier.testTag(TimeTrackerTestTags.HOME_CANCEL_EDIT_BUTTON),
        )
        TimeTrackerPrimaryButton(
            text = "Save Home",
            onClick = actions.onSave,
            modifier = Modifier.testTag(TimeTrackerTestTags.HOME_SAVE_PIN_BUTTON),
        )
    }
}

internal data class LocationSectionActions(
    val onUseCurrentLocation: () -> Unit,
    val onEditPin: () -> Unit,
    val onCancelEdit: () -> Unit,
    val onFieldChange: (LocationField, String) -> Unit,
    val onRadiusSelected: (Float) -> Unit,
    val onSaveRadius: () -> Unit,
    val onResolveAddress: () -> Unit,
    val onOpenMap: () -> Unit,
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
internal fun WorkRadiusSlider(selectedRadiusMeters: String, onRadiusSelected: (Float) -> Unit) {
    val selectedMiles = (
        (selectedRadiusMeters.toFloatOrNull() ?: WorkLocation.MINIMUM_RADIUS_METERS) /
            GeofenceRadiusOptions.ONE_MILE_METERS
        ).roundToInt().coerceIn(1, 25)
    TimeTrackerSettingRow(
        label = "Work radius",
        supportingText = "Selected: $selectedMiles ${if (selectedMiles == 1) "mile" else "miles"}",
    ) {
        Slider(
            value = selectedMiles.toFloat(),
            onValueChange = { miles ->
                onRadiusSelected(miles.roundToInt().coerceIn(1, 25) * GeofenceRadiusOptions.ONE_MILE_METERS)
            },
            valueRange = 1f..25f,
            steps = 23,
            modifier = Modifier
                .weight(1f)
                .testTag(TimeTrackerTestTags.WORK_RADIUS_FIELD),
        )
    }
    TimeTrackerMutedText("Large radii can classify nearby travel as worksite time. Changes apply only after Save.")
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
