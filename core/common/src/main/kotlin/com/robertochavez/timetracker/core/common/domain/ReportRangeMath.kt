package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun LocalDate.instantRange(zoneId: ZoneId): InstantRange = InstantRange(
    start = atStartOfDay(zoneId).toInstant(),
    end = plusDays(1).atStartOfDay(zoneId).toInstant(),
)

internal fun WorkSiteSession.toRange(now: Instant): InstantRange? {
    val effectiveEnd = end ?: now
    return if (effectiveEnd > start) InstantRange(start, effectiveEnd) else null
}

internal fun AwaySession.toRange(now: Instant): InstantRange? {
    val effectiveEnd = end ?: now
    return if (effectiveEnd > start) InstantRange(start, effectiveEnd) else null
}

internal data class InstantRange(val start: Instant, val end: Instant) {
    init {
        require(end > start) { "Instant range end must be after start." }
    }

    val duration = java.time.Duration.between(start, end)

    fun intersect(other: InstantRange): InstantRange? {
        val overlapStart = maxOf(start, other.start)
        val overlapEnd = minOf(end, other.end)
        return if (overlapEnd > overlapStart) InstantRange(overlapStart, overlapEnd) else null
    }
}

internal fun List<InstantRange>.merge(): List<InstantRange> {
    if (isEmpty()) return emptyList()
    val sorted = sortedBy { it.start }
    val merged = mutableListOf<InstantRange>()
    var current = sorted.first()
    sorted.drop(1).forEach { next ->
        current = if (!next.start.isAfter(current.end)) {
            InstantRange(current.start, maxOf(current.end, next.end))
        } else {
            merged += current
            next
        }
    }
    merged += current
    return merged
}

internal fun List<InstantRange>.totalDuration(): java.time.Duration =
    fold(java.time.Duration.ZERO) { total, range -> total.plus(range.duration) }

internal fun List<InstantRange>.overlapDuration(other: List<InstantRange>): java.time.Duration =
    flatMap { range -> other.mapNotNull(range::intersect) }
        .merge()
        .totalDuration()

internal fun List<InstantRange>.constrainTo(bounds: List<InstantRange>): List<InstantRange> =
    flatMap { range -> bounds.mapNotNull(range::intersect) }
        .merge()

internal fun List<InstantRange>.subtract(excluded: List<InstantRange>): List<InstantRange> {
    if (isEmpty() || excluded.isEmpty()) return this
    return fold(emptyList<InstantRange>()) { ranges, range ->
        ranges + excluded.fold(listOf(range)) { remaining, excludedRange ->
            remaining.flatMap { it.subtract(excludedRange) }
        }
    }.merge()
}

private fun InstantRange.subtract(excluded: InstantRange): List<InstantRange> {
    val overlap = intersect(excluded) ?: return listOf(this)
    val before = if (overlap.start > start) InstantRange(start, overlap.start) else null
    val after = if (overlap.end < end) InstantRange(overlap.end, end) else null
    return listOfNotNull(before, after)
}
