package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.SummaryStatus
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ProjectDaySummaryCalculatorTest {
    private val zone = ZoneId.of("America/New_York")

    @Test fun `multiple visits become one project day aggregate`() {
        val sessions = listOf(
            session("a", "2026-08-20T12:00:00Z", "2026-08-20T13:00:00Z"),
            session("b", "2026-08-20T14:00:00Z", "2026-08-20T14:35:00Z"),
        )
        val result = ProjectDaySummaryCalculator.calculate(sessions, zone, Instant.parse("2026-08-21T12:00:00Z")).single()
        assertEquals(95, result.onsiteMinutes)
        assertEquals(2, result.visitCount)
        assertEquals(SummaryStatus.FINAL, result.status)
        assertEquals("project-day:v1:site-1:2026-08-20", result.sourceId)
    }

    @Test fun `session crossing midnight splits by local date across DST safe boundaries`() {
        val result = ProjectDaySummaryCalculator.calculate(
            listOf(session("a", "2026-11-01T03:30:00Z", "2026-11-01T07:30:00Z")),
            zone,
            Instant.parse("2026-11-02T12:00:00Z"),
        )
        assertEquals(listOf(30L, 210L), result.map { it.onsiteMinutes })
    }

    @Test fun `active current session is provisional`() {
        val session = WorkSiteSession("a", "site-1", Instant.parse("2026-08-20T12:00:00Z"), null, "Test", "TEST-001")
        val result = ProjectDaySummaryCalculator.calculate(listOf(session), zone, Instant.parse("2026-08-20T13:00:00Z")).single()
        assertEquals(SummaryStatus.PROVISIONAL, result.status)
    }

    private fun session(id: String, start: String, end: String) = WorkSiteSession(
        id,
        "site-1",
        Instant.parse(start),
        Instant.parse(end),
        "Test Worksite",
        "TEST-001",
    )
}
