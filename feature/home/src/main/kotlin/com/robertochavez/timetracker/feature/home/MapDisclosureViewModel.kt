package com.robertochavez.timetracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapDisclosureViewModel @Inject constructor(private val appSettingsRepository: AppSettingsRepository) : ViewModel() {
    val accepted: StateFlow<Boolean> = appSettingsRepository.settings
        .map { it.mapDisclosureAccepted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun accept() {
        viewModelScope.launch { appSettingsRepository.setMapDisclosureAccepted(true) }
    }

    fun decline() {
        viewModelScope.launch { appSettingsRepository.setMapDisclosureAccepted(false) }
    }
}
