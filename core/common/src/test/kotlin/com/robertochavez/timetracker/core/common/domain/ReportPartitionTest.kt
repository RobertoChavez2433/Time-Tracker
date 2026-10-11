package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.DailyReport
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReportPartitionTest {
    private val zone = ZoneId.of("America/New_York")
    private val calculator = ReportCalculator(zone, WorkSchedule(DayOfWeek.entries.toSet()), PayPeriodSettings(LocalDate.of(2026, 1, 5)))

    @Test fun `legacy review and gaps become idle without mutating evidence`() {
        val start = Instant.parse("2026-08-24T12:00:00Z")
        val session = AwaySession("session", start, start.plusSeconds(3600))
        val legacy = ActivityInterval("legacy", session.id, ActivityBucket.NEEDS_REVIEW, start.plusSeconds(60), start.plusSeconds(1800))
        val snapshot = legacy.copy()
        val report = calculator.daily(LocalDate.of(2026, 8, 24), listOf(session), listOf(legacy), start.plusSeconds(7200))
        assertEquals(Duration.ofHours(1), report.idle)
        assertEquals(snapshot, legacy)
        assertPartition(report)
    }

    @Test fun `overlapping and excluded sessions cannot double count the partition`() {
        val start = Instant.parse("2026-08-24T12:00:00Z")
        val first = AwaySession("one", start, start.plusSeconds(3600))
        val second = AwaySession("two", start.plusSeconds(1800), start.plusSeconds(7200))
        val excluded = AwaySession("excluded", start, start.plusSeconds(10800), countsTowardTotals = false)
        val intervals = listOf(
            ActivityInterval("drive", first.id, ActivityBucket.DRIVE, start, start.plusSeconds(3600)),
            ActivityInterval("site", second.id, ActivityBucket.SITE, start.plusSeconds(1800), start.plusSeconds(5400)),
            ActivityInterval("excluded-drive", excluded.id, ActivityBucket.DRIVE, start, start.plusSeconds(10800)),
        )
        val report = calculator.daily(LocalDate.of(2026, 8, 24), listOf(first, second, excluded), intervals, start.plusSeconds(14400))
        assertEquals(Duration.ofHours(2), report.work)
        assertEquals(Duration.ofMinutes(30), report.drive)
        assertEquals(Duration.ofHours(1), report.site)
        assertEquals(Duration.ofMinutes(30), report.idle)
        assertPartition(report)
    }

    @Test fun `spring and fall daylight saving days partition actual elapsed time`() {
        listOf(LocalDate.of(2026, 3, 8) to 23L, LocalDate.of(2026, 11, 1) to 25L).forEach { (date, hours) ->
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val report = calculator.daily(date, listOf(AwaySession("dst", start, end)), emptyList(), end)
            assertEquals(Duration.ofHours(hours), report.idle)
            assertPartition(report)
        }
    }

    @Test fun `active session crossing midnight accounts for time up to now`() {
        val start = Instant.parse("2026-08-25T03:30:00Z")
        val now = Instant.parse("2026-08-25T05:00:00Z")
        val session = AwaySession("active", start, null)
        val previous = calculator.daily(LocalDate.of(2026, 8, 24), listOf(session), emptyList(), now)
        val current = calculator.daily(LocalDate.of(2026, 8, 25), listOf(session), emptyList(), now)
        assertEquals(Duration.ofMinutes(30), previous.idle)
        assertEquals(Duration.ofHours(1), current.idle)
        assertPartition(previous)
        assertPartition(current)
    }

    private fun assertPartition(report: DailyReport) {
        assertEquals(report.work, report.drive.plus(report.site).plus(report.idle))
        assertEquals(Duration.ZERO, report.needsReview)
    }
}
