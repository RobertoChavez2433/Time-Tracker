package com.robertochavez.timetracker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerCard
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerStatusText

@Composable
fun HomeRoute(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TimeTrackerScreen(modifier = modifier) {
        item {
            TimeTrackerScreenTitle(
                title = "Locations",
                subtitle = "Set home and job-site geofences with adjustable radius.",
            )
        }
        item {
            HomeCurrentCard(state.homeSummary, viewModel::useCurrentHomeLocation)
        }
        item {
            HomePinCard(state, viewModel::updateHomeField, viewModel::saveHomePin)
        }
        item {
            WorkLocationCard(state, viewModel::useCurrentWorkLocation, viewModel::updateWorkField, viewModel::saveWorkPin)
        }
        if (state.statusMessage.isNotBlank()) {
            item {
                TimeTrackerStatusText(state.statusMessage)
            }
        }
    }
}

@Composable
private fun HomeCurrentCard(summary: String, onUseCurrentLocation: () -> Unit) {
    TimeTrackerCard {
        Text("Current Home", style = MaterialTheme.typography.titleMedium)
        TimeTrackerMutedText(summary)
        TimeTrackerPrimaryButton(text = "Use Current Home Location", onClick = onUseCurrentLocation)
    }
}

@Composable
private fun HomePinCard(state: HomeUiState, onFieldChange: (LocationField, String) -> Unit, onSave: () -> Unit) {
    TimeTrackerCard {
        Text("Home Pin", style = MaterialTheme.typography.titleMedium)
        CoordinateRow(
            latitude = state.homeLatitude,
            longitude = state.homeLongitude,
            onLatitudeChange = { onFieldChange(LocationField.LATITUDE, it) },
            onLongitudeChange = { onFieldChange(LocationField.LONGITUDE, it) },
        )
        PinNumberField(
            label = "Radius meters",
            value = state.homeRadiusMeters,
            onValueChange = { onFieldChange(LocationField.RADIUS_METERS, it) },
            modifier = Modifier.fillMaxWidth(),
        )
        TimeTrackerPrimaryButton(text = "Save Home Pin", onClick = onSave)
    }
}

@Composable
private fun WorkLocationCard(
    state: HomeUiState,
    onUseCurrentLocation: () -> Unit,
    onFieldChange: (LocationField, String) -> Unit,
    onSave: () -> Unit,
) {
    TimeTrackerCard {
        Text("Work / Job Site", style = MaterialTheme.typography.titleMedium)
        TimeTrackerMutedText(state.workSummary)
        TimeTrackerPrimaryButton(text = "Use Current Work Location", onClick = onUseCurrentLocation)
        CoordinateRow(
            latitude = state.workLatitude,
            longitude = state.workLongitude,
            onLatitudeChange = { onFieldChange(LocationField.LATITUDE, it) },
            onLongitudeChange = { onFieldChange(LocationField.LONGITUDE, it) },
        )
        PinNumberField(
            label = "Radius meters",
            value = state.workRadiusMeters,
            onValueChange = { onFieldChange(LocationField.RADIUS_METERS, it) },
            modifier = Modifier.fillMaxWidth(),
        )
        TimeTrackerPrimaryButton(text = "Save Work Pin", onClick = onSave)
    }
}

@Composable
private fun CoordinateRow(latitude: String, longitude: String, onLatitudeChange: (String) -> Unit, onLongitudeChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PinNumberField(
            label = "Latitude",
            value = latitude,
            onValueChange = onLatitudeChange,
            modifier = Modifier.weight(1f),
        )
        PinNumberField(
            label = "Longitude",
            value = longitude,
            onValueChange = onLongitudeChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PinNumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier,
    )
}
