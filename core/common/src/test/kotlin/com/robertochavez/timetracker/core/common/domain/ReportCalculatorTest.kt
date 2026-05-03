package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ReportCalculatorTest {
    private val zone = ZoneId.of("America/New_York")
    private val schedule = WorkSchedule(DayOfWeek.entries.toSet())
    private val calculator = ReportCalculator(
        zoneId = zone,
        workSchedule = schedule,
        payPeriodSettings = PayPeriodSettings(LocalDate.of(2026, 4, 27)),
    )

    @Test
    fun `splits sessions across midnight by calendar day`() {
        val session = AwaySession(
            id = "session",
            start = Instant.parse("2026-05-01T03:30:00Z"),
            end = Instant.parse("2026-05-01T05:15:00Z"),
        )

        val slices = SessionSplitter.splitByDay(session, zone, Instant.parse("2026-05-01T06:00:00Z"))

        assertEquals(LocalDate.of(2026, 4, 30), slices[0].date)
        assertEquals(Duration.ofMinutes(30), slices[0].duration)
        assertEquals(LocalDate.of(2026, 5, 1), slices[1].date)
        assertEquals(Duration.ofMinutes(75), slices[1].duration)
    }

    @Test
    fun `ignores non workdays automatically`() {
        val weekdayOnly = ReportCalculator(
            zoneId = zone,
            workSchedule = WorkSchedule(setOf(DayOfWeek.MONDAY)),
            payPeriodSettings = PayPeriodSettings(LocalDate.of(2026, 4, 27)),
        )
        val session = AwaySession(
            id = "session",
            start = Instant.parse("2026-05-02T13:00:00Z"),
            end = Instant.parse("2026-05-02T15:00:00Z"),
        )

        val report = weekdayOnly.daily(
            date = LocalDate.of(2026, 5, 2),
            sessions = listOf(session),
            intervals = emptyList(),
            now = Instant.parse("2026-05-02T16:00:00Z"),
        )

        assertEquals(Duration.ZERO, report.totalAway)
    }

    @Test
    fun `calculates daily weekly biweekly monthly and yearly totals`() {
        val sessions = listOf(
            AwaySession(
                id = "monday",
                start = Instant.parse("2026-04-27T13:00:00Z"),
                end = Instant.parse("2026-04-27T15:00:00Z"),
            ),
            AwaySession(
                id = "friday",
                start = Instant.parse("2026-05-01T13:00:00Z"),
                end = Instant.parse("2026-05-01T16:30:00Z"),
                drivenMiles = 42.5,
            ),
        )
        val now = Instant.parse("2026-05-03T12:00:00Z")

        assertEquals(Duration.ofHours(2), calculator.daily(LocalDate.of(2026, 4, 27), sessions, emptyList(), now).totalAway)
        assertEquals(Duration.ofMinutes(330), calculator.weekly(LocalDate.of(2026, 4, 29), sessions, emptyList(), now).totalAway)
        assertEquals(Duration.ofMinutes(330), calculator.biweekly(LocalDate.of(2026, 5, 3), sessions, emptyList(), now).totalAway)
        assertEquals(Duration.ofMinutes(210), calculator.monthly(YearMonth.of(2026, 5), sessions, emptyList(), now).totalAway)
        assertEquals(Duration.ofMinutes(330), calculator.yearly(2026, sessions, emptyList(), now).totalAway)
        assertEquals(42.5, calculator.weekly(LocalDate.of(2026, 4, 29), sessions, emptyList(), now).drivenMiles, 0.001)
    }

    @Test
    fun `manual edits update reports`() {
        val original = AwaySession(
            id = "session",
            start = Instant.parse("2026-05-01T13:00:00Z"),
            end = Instant.parse("2026-05-01T15:00:00Z"),
        )
        val adjusted = ManualCorrectionService.updateSessionWindow(
            original,
            start = Instant.parse("2026-05-01T13:00:00Z"),
            end = Instant.parse("2026-05-01T16:00:00Z"),
        )

        val report = calculator.daily(
            date = LocalDate.of(2026, 5, 1),
            sessions = listOf(adjusted),
            intervals = emptyList(),
            now = Instant.parse("2026-05-01T17:00:00Z"),
        )

        assertEquals(Duration.ofHours(3), report.totalAway)
        assertEquals(true, adjusted.manuallyAdjusted)
    }

    @Test
    fun `aggregates activity buckets separately from primary total`() {
        val intervals = listOf(
            ActivityInterval(
                id = "drive",
                sessionId = "session",
                bucket = ActivityBucket.DRIVE,
                start = Instant.parse("2026-05-01T13:00:00Z"),
                end = Instant.parse("2026-05-01T14:00:00Z"),
            ),
            ActivityInterval(
                id = "idle",
                sessionId = "session",
                bucket = ActivityBucket.IDLE,
                start = Instant.parse("2026-05-01T14:00:00Z"),
                end = Instant.parse("2026-05-01T14:30:00Z"),
            ),
            ActivityInterval(
                id = "unclassified",
                sessionId = "session",
                bucket = ActivityBucket.UNCLASSIFIED,
                start = Instant.parse("2026-05-01T14:30:00Z"),
                end = Instant.parse("2026-05-01T15:00:00Z"),
            ),
        )

        val totals = ActivityBucketAggregator.aggregate(intervals)

        assertEquals(Duration.ofHours(1), totals[ActivityBucket.DRIVE])
        assertEquals(Duration.ofMinutes(30), totals[ActivityBucket.IDLE])
        assertEquals(Duration.ofMinutes(30), totals[ActivityBucket.UNCLASSIFIED])
    }

    @Test
    fun `jobsite driving miles count while drive time stays excluded`() {
        val session = AwaySession(
            id = "jobsite",
            start = Instant.parse("2026-05-01T13:00:00Z"),
            end = Instant.parse("2026-05-01T14:00:00Z"),
            drivenMiles = 6.75,
        )
        val jobsiteDrivingInterval = ActivityInterval(
            id = "jobsite-driving",
            sessionId = session.id,
            bucket = ActivityBucket.UNCLASSIFIED,
            start = Instant.parse("2026-05-01T13:15:00Z"),
            end = Instant.parse("2026-05-01T13:45:00Z"),
        )

        val report = calculator.daily(
            date = LocalDate.of(2026, 5, 1),
            sessions = listOf(session),
            intervals = listOf(jobsiteDrivingInterval),
            now = Instant.parse("2026-05-01T15:00:00Z"),
        )

        assertEquals(6.75, report.drivenMiles, 0.001)
        assertEquals(Duration.ZERO, report.drive)
        assertEquals(Duration.ofMinutes(30), report.unclassified)
    }

    @Test
    fun `splits driven miles across midnight proportionally`() {
        val session = AwaySession(
            id = "miles",
            start = Instant.parse("2026-05-01T03:00:00Z"),
            end = Instant.parse("2026-05-01T05:00:00Z"),
            drivenMiles = 80.0,
        )

        val previousDay = calculator.daily(
            date = LocalDate.of(2026, 4, 30),
            sessions = listOf(session),
            intervals = emptyList(),
            now = Instant.parse("2026-05-01T06:00:00Z"),
        )
        val nextDay = calculator.daily(
            date = LocalDate.of(2026, 5, 1),
            sessions = listOf(session),
            intervals = emptyList(),
            now = Instant.parse("2026-05-01T06:00:00Z"),
        )

        assertEquals(40.0, previousDay.drivenMiles, 0.001)
        assertEquals(40.0, nextDay.drivenMiles, 0.001)
    }
}
