package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object SessionSplitter {
    fun splitByDay(session: AwaySession, zoneId: ZoneId, now: Instant): List<DailyDurationSlice> = splitInstantRange(
        start = session.start,
        end = session.end ?: now,
        zoneId = zoneId,
    ).map { slice ->
        DailyDurationSlice(
            date = slice.date,
            start = slice.start,
            end = slice.end,
            duration = slice.duration,
        )
    }

    fun splitActivityIntervalByDay(interval: ActivityInterval, zoneId: ZoneId): List<DailyActivitySlice> = splitInstantRange(
        start = interval.start,
        end = interval.end,
        zoneId = zoneId,
    ).map { slice ->
        DailyActivitySlice(
            date = slice.date,
            bucket = interval.bucket,
            start = slice.start,
            end = slice.end,
            duration = slice.duration,
        )
    }

    private fun splitInstantRange(start: Instant, end: Instant, zoneId: ZoneId): List<RangeSlice> {
        require(end > start) { "Range end must be after start." }

        val slices = mutableListOf<RangeSlice>()
        var cursor = start
        while (cursor < end) {
            val cursorDate = cursor.atZone(zoneId).toLocalDate()
            val nextMidnight = cursorDate.plusDays(1).atStartOfDay(zoneId).toInstant()
            val sliceEnd = minOf(end, nextMidnight)
            slices += RangeSlice(
                date = cursorDate,
                start = cursor,
                end = sliceEnd,
            )
            cursor = sliceEnd
        }
        return slices
    }
}

data class DailyDurationSlice(val date: LocalDate, val start: Instant, val end: Instant, val duration: Duration)

data class DailyActivitySlice(
    val date: LocalDate,
    val bucket: ActivityBucket,
    val start: Instant,
    val end: Instant,
    val duration: Duration,
)

private data class RangeSlice(val date: LocalDate, val start: Instant, val end: Instant) {
    val duration: Duration = Duration.between(start, end)
}
