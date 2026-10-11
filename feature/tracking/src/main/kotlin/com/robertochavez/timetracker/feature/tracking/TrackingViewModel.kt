@file:Suppress("TooGenericExceptionCaught") // Surface platform/persistence failures without swallowing cancellation.

package com.robertochavez.timetracker.feature.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TrackingUiState(
    val activeSummary: String = "No active away session",
    val hasActiveSession: Boolean = false,
    val busy: Boolean = false,
    val error: String = "",
)

@HiltViewModel
class TrackingViewModel @Inject constructor(private val tracking: TrackingRepository, private val clock: Clock) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow("")
    val uiState = combine(tracking.observeActiveSession(), busy, error) { active, busy, error ->
        TrackingUiState(
            active?.let { "Tracking since ${it.start.atZone(clock.zone).format(DateTimeFormatter.ofPattern("h:mm a"))}" }
                ?: "No active away session",
            active != null,
            busy,
            error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackingUiState())

    fun startManualSession() = perform { tracking.startManualSession(clock.instant()) }
    fun stopActiveSession() = perform { tracking.stopActiveAwaySession(clock.instant()) }
    private fun perform(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                action()
                error.value = ""
            } catch (
                cancelled: CancellationException,
            ) {
                throw cancelled
            } catch (
                failure: Exception,
            ) {
                error.value = failure.message ?: "Tracking action failed. Try again."
            } finally {
                busy.value = false
            }
        }
    }
}
