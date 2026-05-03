package com.robertochavez.timetracker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSecondaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSettingSection
import com.robertochavez.timetracker.core.designsystem.TimeTrackerStatusText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@Composable
fun HomeRoute(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
            HomeLocationSection(state, viewModel::useCurrentHomeLocation, viewModel::updateHomeField, viewModel::saveHomePin)
        }
        item {
            WorkLocationSection(state, viewModel::useCurrentWorkLocation, viewModel::updateWorkField, viewModel::saveWorkPin)
        }
        if (state.statusMessage.isNotBlank()) {
            item {
                TimeTrackerStatusText(state.statusMessage)
            }
        }
    }
}

@Composable
private fun LocationStatusSection(homeSummary: String, workSummary: String) {
    TimeTrackerSettingSection(title = "Setup status") {
        TimeTrackerMutedText("Home: $homeSummary")
        TimeTrackerMutedText("Work: $workSummary")
    }
}

@Composable
private fun HomeLocationSection(
    state: HomeUiState,
    onUseCurrentLocation: () -> Unit,
    onFieldChange: (LocationField, String) -> Unit,
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
        PinNumberField(
            label = "Radius meters",
            value = state.homeRadiusMeters,
            onValueChange = { onFieldChange(LocationField.RADIUS_METERS, it) },
            modifier = Modifier.fillMaxWidth(),
            testTag = TimeTrackerTestTags.HOME_RADIUS_FIELD,
        )
        TimeTrackerPrimaryButton(
            text = "Save Home Pin",
            onClick = onSave,
            modifier = Modifier.testTag(TimeTrackerTestTags.HOME_SAVE_PIN_BUTTON),
        )
    }
}

@Composable
private fun WorkLocationSection(
    state: HomeUiState,
    onUseCurrentLocation: () -> Unit,
    onFieldChange: (LocationField, String) -> Unit,
    onSave: () -> Unit,
) {
    TimeTrackerSettingSection(title = "Work / job site", subtitle = "Job-site driving stays out of commute totals.") {
        TimeTrackerMutedText(state.workSummary)
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
        PinNumberField(
            label = "Radius meters",
            value = state.workRadiusMeters,
            onValueChange = { onFieldChange(LocationField.RADIUS_METERS, it) },
            modifier = Modifier.fillMaxWidth(),
            testTag = TimeTrackerTestTags.WORK_RADIUS_FIELD,
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
        )
        PinNumberField(
            label = "Longitude",
            value = longitude,
            onValueChange = onLongitudeChange,
            modifier = Modifier.weight(1f),
            testTag = longitudeTag,
        )
    }
}

@Composable
private fun PinNumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, testTag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.testTag(testTag),
    )
}
