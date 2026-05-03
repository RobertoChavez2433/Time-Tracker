package com.robertochavez.timetracker.feature.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val trackingRepository: TrackingRepository,
    private val clock: Clock,
    private val logger: AppLogger,
) : ViewModel() {
    private val edits = MutableStateFlow<Map<String, SessionEdit>>(emptyMap())

    val uiState: StateFlow<TrackingUiState> = combine(
        trackingRepository.observeSessions(),
        edits,
    ) { sessions, edits ->
        val active = sessions.firstOrNull { it.isActive }
        TrackingUiState(
            activeSummary = active?.let { "Active since ${it.start}" } ?: "No active away session",
            hasActiveSession = active != null,
            sessions = sessions.map { session ->
                val edit = edits[session.id] ?: SessionEdit(
                    start = session.start.toString(),
                    end = session.end?.toString().orEmpty(),
                    drivenMiles = session.drivenMiles.toString(),
                )
                SessionUiModel(
                    id = session.id,
                    title = session.title(),
                    subtitle = session.subtitle(),
                    countsTowardTotals = session.countsTowardTotals,
                    editStart = edit.start,
                    editEnd = edit.end,
                    editDrivenMiles = edit.drivenMiles,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackingUiState())

    fun startManualSession() {
        logger.info(LogCategory.UI, "Manual away session start requested")
        viewModelScope.launch {
            trackingRepository.startManualSession(Instant.now(clock))
        }
    }

    fun stopActiveSession() {
        logger.info(LogCategory.UI, "Active away session stop requested")
        viewModelScope.launch {
            trackingRepository.stopActiveAwaySession(Instant.now(clock))
        }
    }

    fun setCountsTowardTotals(sessionId: String, countsTowardTotals: Boolean) {
        logger.info(
            LogCategory.UI,
            "Session totals toggle requested",
            mapOf("session" to sessionId.take(8), "countsTowardTotals" to countsTowardTotals),
        )
        viewModelScope.launch {
            trackingRepository.setCountsTowardTotals(sessionId, countsTowardTotals)
        }
    }

    fun updateEditStart(sessionId: String, value: String) {
        updateEdit(sessionId) { it.copy(start = value) }
    }

    fun updateEditEnd(sessionId: String, value: String) {
        updateEdit(sessionId) { it.copy(end = value) }
    }

    fun updateEditDrivenMiles(sessionId: String, value: String) {
        updateEdit(sessionId) { it.copy(drivenMiles = value) }
    }

    fun saveSessionCorrections(sessionId: String) {
        logger.info(LogCategory.UI, "Session correction save requested", mapOf("session" to sessionId.take(8)))
        viewModelScope.launch {
            val edit = edits.value[sessionId] ?: return@launch
            val start = Instant.parse(edit.start)
            val end = edit.end.takeIf { it.isNotBlank() }?.let(Instant::parse)
            val drivenMiles = edit.drivenMiles.toDoubleOrNull() ?: return@launch
            trackingRepository.updateSessionWindow(sessionId, start, end)
            trackingRepository.setDrivenMiles(sessionId, drivenMiles)
        }
    }

    private fun updateEdit(sessionId: String, transform: (SessionEdit) -> SessionEdit) {
        val current = edits.value[sessionId] ?: SessionEdit()
        edits.value = edits.value + (sessionId to transform(current))
    }
}

data class TrackingUiState(
    val activeSummary: String = "No active away session",
    val hasActiveSession: Boolean = false,
    val sessions: List<SessionUiModel> = emptyList(),
)

data class SessionUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val countsTowardTotals: Boolean,
    val editStart: String,
    val editEnd: String,
    val editDrivenMiles: String,
)

private data class SessionEdit(val start: String = "", val end: String = "", val drivenMiles: String = "0.0")

private fun AwaySession.title(): String = if (isActive) {
    "Active session"
} else {
    "Session ${id.take(8)}"
}

private fun AwaySession.subtitle(): String {
    val endText = end?.toString() ?: "active"
    val adjusted = if (manuallyAdjusted) " manually adjusted" else ""
    return "$start to $endText, ${"%.1f".format(drivenMiles)} miles$adjusted"
}
