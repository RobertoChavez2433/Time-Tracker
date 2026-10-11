@file:Suppress("TooGenericExceptionCaught") // Surface platform/persistence failures without swallowing cancellation.

package com.robertochavez.timetracker.feature.tracking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

data class CorrectionState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val start: String = "",
    val end: String = "",
    val miles: String = "",
    val included: Boolean = true,
    val error: String = "",
    val dirty: Boolean = false,
)

@HiltViewModel
class SessionCorrectionViewModel @Inject constructor(
    private val repository: TrackingRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val id = requireNotNull(savedState.get<String>("sessionId"))
    private val mutableState = MutableStateFlow(CorrectionState())
    val state = mutableState.asStateFlow()
    private var baseline = emptyList<String>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val session = requireNotNull(repository.getSession(id)) { "This session no longer exists." }
                baseline =
                    listOf(
                        session.start.toString(),
                        session.end?.toString().orEmpty(),
                        session.drivenMiles.toString(),
                        session.countsTowardTotals.toString(),
                    )
                val values = savedState.get<ArrayList<String>>("draft") ?: baseline
                mutableState.value =
                    CorrectionState(
                        false,
                        start = values[0],
                        end = values[1],
                        miles = values[2],
                        included = values[3].toBoolean(),
                        dirty =
                        values != baseline,
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value =
                    CorrectionState(loading = false, error = failure.message ?: "Could not load session.")
            }
        }
    }

    fun edit(
        start: String = state.value.start,
        end: String = state.value.end,
        miles: String = state.value.miles,
        included: Boolean = state.value.included,
    ) {
        if (state.value.saving || state.value.saved) return
        val values = arrayListOf(start, end, miles, included.toString())
        savedState["draft"] = values
        mutableState.value =
            state.value.copy(start = start, end = end, miles = miles, included = included, error = "", dirty = values != baseline)
    }

    fun save() {
        if (state.value.loading || state.value.saving || state.value.saved) return
        val draft = state.value
        mutableState.value = draft.copy(saving = true, error = "")
        viewModelScope.launch {
            try {
                val (start, end, miles) = validate(draft)
                val existing = requireNotNull(repository.getSession(id)) { "This session no longer exists." }
                require(end != null || existing.isActive) { "A completed session requires an end time." }
                requireNotNull(repository.saveCorrection(id, start, end, miles, draft.included)) { "This session no longer exists." }
                savedState.remove<ArrayList<String>>("draft")
                mutableState.value = draft.copy(saved = true, dirty = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = draft.copy(error = failure.message ?: "Could not save. Your changes have not been saved.")
            }
        }
    } private fun validate(draft: CorrectionState): Triple<Instant, Instant?, Double> {
        val start = parseTime(draft.start)
        val end = draft.end.takeIf(String::isNotBlank)?.let(::parseTime)
        val miles = requireNotNull(draft.miles.toDoubleOrNull()) { "Enter valid miles." }
        require(miles.isFinite() && miles >= 0) { "Miles must be a finite, non-negative number." }
        require(end == null || end > start) { "End must be after start." }
        require(start <= clock.instant() && (end == null || end <= clock.instant())) { "Session times cannot be in the future." }
        return Triple(start, end, miles)
    }
}

private fun parseTime(value: String): Instant = try {
    Instant.parse(value.trim())
} catch (_: java.time.format.DateTimeParseException) {
    throw IllegalArgumentException("Use ISO date/time with a timezone, such as 2026-08-30T08:00:00-04:00.")
}
