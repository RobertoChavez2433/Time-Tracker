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
internal fun HomeLocationSection(
    state: HomeUiState,
    onUseCurrentLocation: () -> Unit,
    onFieldChange: (LocationField, String) -> Unit,
    onRadiusSelected: (Float) -> Unit,
    onSave: () -> Unit,
) {
    TimeTrackerSettingSection(title = "Home geofence", subtitle = "Use current GPS or enter a pin and radius.") {
        TimeTrackerPrimaryButton(
            text = "Use Current Home Location",
            onClick = onUseCurrentLocation,
            modifier = Modifier.testTag(TimeTrackerTestTags.HOME_USE_CURRENT_BUTTON),
        )
        CoordinateRow(
            latitude = state.homeLatitude,
            longitude = state.homeLongitude,
            onLatitudeChange = { onFieldChange(LocationField.LATITUDE, it) },
            onLongitudeChange = { onFieldChange(LocationField.LONGITUDE, it) },
            latitudeTag = TimeTrackerTestTags.HOME_LATITUDE_FIELD,
            longitudeTag = TimeTrackerTestTags.HOME_LONGITUDE_FIELD,
        )
        RadiusSelector(
            title = "Home radius",
            selectedRadiusMeters = state.homeRadiusMeters,
            tagPrefix = "home",
            onRadiusSelected = onRadiusSelected,
        )
        TimeTrackerPrimaryButton(
            text = "Save Home Pin",
            onClick = onSave,
            modifier = Modifier.testTag(TimeTrackerTestTags.HOME_SAVE_PIN_BUTTON),
        )
    }
}

@Composable
internal fun WorkLocationSection(
    state: HomeUiState,
    onUseCurrentLocation: () -> Unit,
    onFieldChange: (LocationField, String) -> Unit,
    onRadiusSelected: (Float) -> Unit,
    onSave: () -> Unit,
) {
    TimeTrackerSettingSection(title = "Work / job site", subtitle = "Job-site driving stays out of commute totals.") {
        TimeTrackerMutedText(state.workSummary)
        state.workLocations.forEach { summary ->
            TimeTrackerMutedText(summary)
        }
        TimeTrackerSecondaryButton(
            text = "Use Current Work Location",
            onClick = onUseCurrentLocation,
            modifier = Modifier.testTag(TimeTrackerTestTags.WORK_USE_CURRENT_BUTTON),
        )
        CoordinateRow(
            latitude = state.workLatitude,
            longitude = state.workLongitude,
            onLatitudeChange = { onFieldChange(LocationField.LATITUDE, it) },
            onLongitudeChange = { onFieldChange(LocationField.LONGITUDE, it) },
            latitudeTag = TimeTrackerTestTags.WORK_LATITUDE_FIELD,
            longitudeTag = TimeTrackerTestTags.WORK_LONGITUDE_FIELD,
        )
        RadiusSelector(
            title = "Work radius",
            selectedRadiusMeters = state.workRadiusMeters,
            tagPrefix = "work",
            onRadiusSelected = onRadiusSelected,
        )
        TimeTrackerSecondaryButton(
            text = "Save Work Pin",
            onClick = onSave,
            modifier = Modifier.testTag(TimeTrackerTestTags.WORK_SAVE_PIN_BUTTON),
        )
    }
}

@Composable
private fun CoordinateRow(
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
private fun RadiusSelector(title: String, selectedRadiusMeters: String, tagPrefix: String, onRadiusSelected: (Float) -> Unit) {
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
