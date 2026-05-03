package com.robertochavez.timetracker.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeRoute(modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Locations", style = MaterialTheme.typography.headlineMedium)
            HomeCurrentCard(state.homeSummary, viewModel::useCurrentHomeLocation)
            HomePinCard(state, viewModel::updateHomeField, viewModel::saveHomePin)
            WorkLocationCard(state, viewModel::useCurrentWorkLocation, viewModel::updateWorkField, viewModel::saveWorkPin)
            if (state.statusMessage.isNotBlank()) {
                Text(state.statusMessage, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun HomeCurrentCard(summary: String, onUseCurrentLocation: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Current Home", style = MaterialTheme.typography.titleMedium)
            Text(summary)
            Button(onClick = onUseCurrentLocation) {
                Text("Use Current Home Location")
            }
        }
    }
}

@Composable
private fun HomePinCard(state: HomeUiState, onFieldChange: (LocationField, String) -> Unit, onSave: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
            Button(onClick = onSave) {
                Text("Save Home Pin")
            }
        }
    }
}

@Composable
private fun WorkLocationCard(
    state: HomeUiState,
    onUseCurrentLocation: () -> Unit,
    onFieldChange: (LocationField, String) -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Work / Job Site", style = MaterialTheme.typography.titleMedium)
            Text(state.workSummary)
            Button(onClick = onUseCurrentLocation) {
                Text("Use Current Work Location")
            }
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
            Button(onClick = onSave) {
                Text("Save Work Pin")
            }
        }
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
