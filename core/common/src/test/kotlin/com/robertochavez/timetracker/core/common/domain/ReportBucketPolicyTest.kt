package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReportBucketPolicyTest {
    private val calculator = ReportCalculator(
        zoneId = ZoneId.of("America/New_York"),
        workSchedule = WorkSchedule(DayOfWeek.entries.toSet()),
        payPeriodSettings = PayPeriodSettings(LocalDate.of(2026, 4, 27)),
    )

    @Test
    fun `driving inside site increases miles but not drive time`() {
        val session = AwaySession(
            id = "jobsite",
            start = Instant.parse("2026-05-01T13:00:00Z"),
            end = Instant.parse("2026-05-01T14:00:00Z"),
            drivenMiles = 6.75,
        )
        val jobsiteDrivingInterval = ActivityInterval(
            id = "jobsite-driving",
            sessionId = session.id,
            bucket = ActivityBucket.NEEDS_REVIEW,
            start = Instant.parse("2026-05-01T13:15:00Z"),
            end = Instant.parse("2026-05-01T13:45:00Z"),
        )
        val siteSessions = listOf(
            WorkSiteSession(
                id = "site",
                workLocationId = "work",
                start = Instant.parse("2026-05-01T13:15:00Z"),
                end = Instant.parse("2026-05-01T13:45:00Z"),
            ),
        )

        val report = calculator.daily(
            date = LocalDate.of(2026, 5, 1),
            sessions = listOf(session),
            intervals = listOf(jobsiteDrivingInterval),
            now = Instant.parse("2026-05-01T15:00:00Z"),
            workSiteSessions = siteSessions,
        )

        assertEquals(6.75, report.drivenMiles, 0.001)
        assertEquals(Duration.ZERO, report.drive)
        assertEquals(Duration.ofMinutes(30), report.site)
        assertEquals(Duration.ZERO, report.idle)
        assertEquals(Duration.ofMinutes(30), report.needsReview)
    }

    @Test
    fun `daily report partitions confirmed work buckets and review time`() {
        val session = AwaySession(
            id = "session",
            start = Instant.parse("2026-05-01T13:00:00Z"),
            end = Instant.parse("2026-05-01T17:00:00Z"),
        )
        val intervals = listOf(
            interval("drive", session.id, ActivityBucket.DRIVE, "2026-05-01T13:00:00Z", "2026-05-01T14:00:00Z"),
            interval("site-drive", session.id, ActivityBucket.DRIVE, "2026-05-01T15:00:00Z", "2026-05-01T15:30:00Z"),
            interval("idle", session.id, ActivityBucket.IDLE, "2026-05-01T16:00:00Z", "2026-05-01T17:00:00Z"),
        )
        val siteSessions = listOf(
            WorkSiteSession(
                id = "site",
                workLocationId = "work",
                start = Instant.parse("2026-05-01T14:00:00Z"),
                end = Instant.parse("2026-05-01T16:00:00Z"),
            ),
        )

        val report = calculator.daily(
            date = LocalDate.of(2026, 5, 1),
            sessions = listOf(session),
            intervals = intervals,
            now = Instant.parse("2026-05-01T18:00:00Z"),
            workSiteSessions = siteSessions,
        )

        assertEquals(Duration.ofHours(4), report.totalAway)
        assertEquals(Duration.ofHours(1), report.drive)
        assertEquals(Duration.ofHours(2), report.site)
        assertEquals(Duration.ofHours(1), report.idle)
        assertEquals(Duration.ZERO, report.needsReview)
    }

    @Test
    fun `home wins over drive site idle needs review and miles`() {
        val session = AwaySession(
            id = "session",
            start = Instant.parse("2026-05-01T13:00:00Z"),
            end = Instant.parse("2026-05-01T15:00:00Z"),
            drivenMiles = 20.0,
        )
        val intervals = listOf(
            interval("home", session.id, ActivityBucket.HOME, "2026-05-01T13:00:00Z", "2026-05-01T14:00:00Z"),
            interval("drive-home", session.id, ActivityBucket.DRIVE, "2026-05-01T13:15:00Z", "2026-05-01T13:45:00Z"),
            interval("site-overlap", session.id, ActivityBucket.SITE, "2026-05-01T13:30:00Z", "2026-05-01T14:30:00Z"),
            interval("idle-home", session.id, ActivityBucket.IDLE, "2026-05-01T14:30:00Z", "2026-05-01T15:00:00Z"),
            interval("review-home", session.id, ActivityBucket.NEEDS_REVIEW, "2026-05-01T13:00:00Z", "2026-05-01T14:00:00Z"),
        )

        val report = calculator.daily(
            date = LocalDate.of(2026, 5, 1),
            sessions = listOf(session),
            intervals = intervals,
            now = Instant.parse("2026-05-01T16:00:00Z"),
        )

        assertEquals(Duration.ofHours(1), report.totalAway)
        assertEquals(Duration.ZERO, report.drive)
        assertEquals(Duration.ofMinutes(30), report.site)
        assertEquals(Duration.ofMinutes(30), report.idle)
        assertEquals(Duration.ZERO, report.needsReview)
        assertEquals(10.0, report.drivenMiles, 0.001)
        assertEquals(report.totalAway, report.work)
    }

    @Test
    fun `uncertain away time goes to needs review instead of idle`() {
        val session = AwaySession(
            id = "unsaved-stop",
            start = Instant.parse("2026-05-01T13:00:00Z"),
            end = Instant.parse("2026-05-01T14:00:00Z"),
        )

        val report = calculator.daily(
            date = LocalDate.of(2026, 5, 1),
            sessions = listOf(session),
            intervals = emptyList(),
            now = Instant.parse("2026-05-01T15:00:00Z"),
        )

        assertEquals(Duration.ZERO, report.drive)
        assertEquals(Duration.ZERO, report.site)
        assertEquals(Duration.ZERO, report.idle)
        assertEquals(Duration.ofHours(1), report.needsReview)
        assertEquals(report.totalAway, report.work)
        assertEquals(Duration.ZERO, report.drive.plus(report.site).plus(report.idle))
    }

    @Test
    fun `report view moves drive inside saved site to site and leaves uncertain time for review`() {
        val session = AwaySession(
            id = "historical",
            start = Instant.parse("2026-05-01T13:00:00Z"),
            end = Instant.parse("2026-05-01T15:00:00Z"),
        )
        val misclassifiedDrive = interval(
            id = "drive-inside-site",
            sessionId = session.id,
            bucket = ActivityBucket.DRIVE,
            start = "2026-05-01T13:30:00Z",
            end = "2026-05-01T14:30:00Z",
        )
        val siteSession = WorkSiteSession(
            id = "site",
            workLocationId = "work",
            start = Instant.parse("2026-05-01T13:30:00Z"),
            end = Instant.parse("2026-05-01T14:30:00Z"),
        )

        val report = calculator.daily(
            date = LocalDate.of(2026, 5, 1),
            sessions = listOf(session),
            intervals = listOf(misclassifiedDrive),
            now = Instant.parse("2026-05-01T16:00:00Z"),
            workSiteSessions = listOf(siteSession),
        )

        assertEquals(Duration.ZERO, report.drive)
        assertEquals(Duration.ofHours(1), report.site)
        assertEquals(Duration.ZERO, report.idle)
        assertEquals(Duration.ofHours(1), report.needsReview)
        assertEquals(report.totalAway, report.work)
    }

    private fun interval(id: String, sessionId: String, bucket: ActivityBucket, start: String, end: String): ActivityInterval =
        ActivityInterval(
            id = id,
            sessionId = sessionId,
            bucket = bucket,
            start = Instant.parse(start),
            end = Instant.parse(end),
        )
}
