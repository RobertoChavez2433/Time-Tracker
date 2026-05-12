package com.robertochavez.timetracker.feature.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import com.robertochavez.timetracker.core.logging.warn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val trackingRepository: TrackingRepository,
    private val clock: Clock,
    private val logger: AppLogger,
) : ViewModel() {
    private val edits = MutableStateFlow<Map<String, SessionEdit>>(emptyMap())
    private val editingSessionIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<TrackingUiState> = combine(
        trackingRepository.observeSessions(),
        trackingRepository.observeActivityIntervals(),
        edits,
        editingSessionIds,
    ) { sessions, intervals, edits, editingIds ->
        val active = sessions.firstOrNull { it.isActive }
        val now = Instant.now(clock)
        TrackingUiState(
            activeSummary = active?.let { "Active since ${it.start.formatTime(clock.zone)}" } ?: "No active away session",
            hasActiveSession = active != null,
            sessions = sessions.map { session ->
                val edit = edits[session.id]
                val sessionIntervals = intervals.filter { it.sessionId == session.id }
                SessionUiModel(
                    id = session.id,
                    title = session.title(now, clock.zone),
                    subtitle = session.subtitle(now),
                    duration = session.durationLabel(now),
                    miles = session.drivenMiles.formatMiles(),
                    inclusionStatus = if (session.countsTowardTotals) "Included in dashboard" else "Excluded from dashboard",
                    classificationSummary = sessionIntervals.classificationSummary(),
                    countsTowardTotals = session.countsTowardTotals,
                    isEditing = session.id in editingIds,
                    editStart = edit?.start ?: session.start.formatInput(clock.zone),
                    editEnd = edit?.end ?: session.end?.formatInput(clock.zone).orEmpty(),
                    editDrivenMiles = edit?.drivenMiles ?: session.drivenMiles.toString(),
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

    fun editSession(sessionId: String) {
        editingSessionIds.value = editingSessionIds.value + sessionId
    }

    fun cancelEdit(sessionId: String) {
        editingSessionIds.value = editingSessionIds.value - sessionId
        edits.value = edits.value - sessionId
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
            val existing = trackingRepository.observeSessions().first().firstOrNull { it.id == sessionId } ?: return@launch
            val edit = edits.value[sessionId] ?: SessionEdit()
            val startText = edit.start ?: existing.start.toString()
            val endText = edit.end ?: existing.end?.toString().orEmpty()
            val milesText = edit.drivenMiles ?: existing.drivenMiles.toString()
            val start = parseInstantOrNull(sessionId, "start", startText) ?: return@launch
            val end = endText.takeIf { it.isNotBlank() }?.let {
                parseInstantOrNull(sessionId, "end", it) ?: return@launch
            }
            val drivenMiles = milesText.toDoubleOrNull() ?: run {
                logger.warn(
                    LogCategory.UI,
                    "Invalid session correction miles",
                    mapOf("session" to sessionId.take(8), "value" to milesText),
                )
                return@launch
            }
            trackingRepository.updateSessionWindow(sessionId, start, end)
            trackingRepository.setDrivenMiles(sessionId, drivenMiles)
            editingSessionIds.value = editingSessionIds.value - sessionId
            edits.value = edits.value - sessionId
        }
    }

    private fun updateEdit(sessionId: String, transform: (SessionEdit) -> SessionEdit) {
        val current = edits.value[sessionId] ?: SessionEdit()
        edits.value = edits.value + (sessionId to transform(current))
    }

    private fun parseInstantOrNull(sessionId: String, field: String, value: String): Instant? =
        parseInstant(value, clock.zone).getOrElse { error ->
            logger.warn(
                LogCategory.UI,
                "Invalid session correction instant",
                mapOf("session" to sessionId.take(8), "field" to field, "value" to value),
                error,
            )
            null
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
    val duration: String,
    val miles: String,
    val inclusionStatus: String,
    val classificationSummary: String,
    val countsTowardTotals: Boolean,
    val isEditing: Boolean,
    val editStart: String,
    val editEnd: String,
    val editDrivenMiles: String,
)

private data class SessionEdit(val start: String? = null, val end: String? = null, val drivenMiles: String? = null)

private fun AwaySession.title(now: Instant, zoneId: ZoneId): String {
    val today = now.atZone(zoneId).toLocalDate()
    val startDateTime = start.atZone(zoneId)
    val dateText = if (startDateTime.toLocalDate() == today) {
        "Today"
    } else {
        startDateTime.format(SESSION_DATE_FORMATTER)
    }
    val endText = end?.formatTime(zoneId) ?: "Active"
    return "$dateText, ${start.formatTime(zoneId)} - $endText"
}

private fun AwaySession.subtitle(now: Instant): String {
    val adjusted = if (manuallyAdjusted) " manually adjusted" else ""
    return "${durationLabel(now)}, ${drivenMiles.formatMiles()} miles$adjusted"
}

private fun AwaySession.durationLabel(now: Instant): String = Duration.between(start, end ?: now).coerceNonNegative().formatDuration()

private fun List<ActivityInterval>.classificationSummary(): String {
    val totals = groupBy({ it.bucket }, { Duration.between(it.start, it.end) })
        .mapValues { (_, durations) -> durations.fold(Duration.ZERO, Duration::plus) }
    val parts = listOf(
        ActivityBucket.DRIVE to "Drive",
        ActivityBucket.IDLE to "Idle",
        ActivityBucket.UNCLASSIFIED to "Other",
    ).mapNotNull { (bucket, label) ->
        val duration = totals[bucket]?.takeUnless { it.isZero } ?: return@mapNotNull null
        "$label ${duration.formatDuration()}"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "No activity classification"
}

private fun Instant.formatInput(zoneId: ZoneId): String = atZone(zoneId).format(INPUT_FORMATTER)

private fun Instant.formatTime(zoneId: ZoneId): String = atZone(zoneId).format(TIME_FORMATTER)

private fun parseInstant(value: String, zoneId: ZoneId): Result<Instant> = runCatching {
    runCatching { Instant.parse(value) }.getOrElse {
        LocalDateTime.parse(value, INPUT_FORMATTER).atZone(zoneId).toInstant()
    }
}

private fun Duration.coerceNonNegative(): Duration = if (isNegative) Duration.ZERO else this

private fun Duration.formatDuration(): String {
    val totalMinutes = toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalMinutes == 0L -> "0m"
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

private fun Double.formatMiles(): String = String.format(Locale.US, "%.1f", this)

private val SESSION_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

private val INPUT_FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendPattern("M/d/yyyy h:mm a")
    .toFormatter(Locale.US)
