package com.robertochavez.timetracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.common.repository.HomeLocationRepository
import com.robertochavez.timetracker.core.common.repository.WorkLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(home: HomeLocationRepository, work: WorkLocationRepository) : ViewModel() {
    val uiState = combine(home.observeHomeLocation(), work.observeWorkLocations()) { home, work ->
        PlacesUiState(home, work)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlacesUiState())
}

data class PlacesUiState(val home: HomeLocation? = null, val work: List<WorkLocation> = emptyList())
data class MapReferenceLocationUiModel(val id: String, val label: String, val latitude: Double, val longitude: Double) {
    companion object {
        const val CURRENT_LOCATION_ID = "current-location"
        const val CURRENT_HOME_ID = "home"
    }
}
