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
            Text("Home", style = MaterialTheme.typography.headlineMedium)
            Text("Set the home geofence used to start and stop away sessions.")

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Current Home", style = MaterialTheme.typography.titleMedium)
                    Text(state.homeSummary)
                    Button(onClick = viewModel::useCurrentLocation) {
                        Text("Use Current Location")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Map Pin", style = MaterialTheme.typography.titleMedium)
                    Text("Adjust the pin coordinates and radius.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PinNumberField(
                            label = "Latitude",
                            value = state.pinLatitude,
                            onValueChange = viewModel::updatePinLatitude,
                            modifier = Modifier.weight(1f),
                        )
                        PinNumberField(
                            label = "Longitude",
                            value = state.pinLongitude,
                            onValueChange = viewModel::updatePinLongitude,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PinNumberField(
                        label = "Radius meters",
                        value = state.pinRadiusMeters,
                        onValueChange = viewModel::updatePinRadiusMeters,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = viewModel::saveMapPin) {
                        Text("Save Home Pin")
                    }
                }
            }

            if (state.statusMessage.isNotBlank()) {
                Text(state.statusMessage, color = MaterialTheme.colorScheme.primary)
            }
        }
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
