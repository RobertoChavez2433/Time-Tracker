package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.common.model.ProjectDaySummary
import com.robertochavez.timetracker.core.common.model.SummaryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class HubPayloadBuilderTest {
    @Test fun `project payload is deterministic and contains no private location fields`() {
        val summary = ProjectDaySummary(
            "project-day:v1:site-1:2026-08-20", LocalDate.parse("2026-08-20"), "site-1", "001-AB",
            "Worksite", Instant.parse("2026-08-20T12:00:00Z"), Instant.parse("2026-08-20T13:00:00Z"), 60, 1, SummaryStatus.FINAL,
        )
        val builder = HubPayloadBuilder()
        val first = builder.projectDay(summary)
        assertEquals(first, builder.projectDay(summary))
        listOf("latitude", "longitude", "address", "radius", "route", "speed", "home").forEach {
            assertFalse(first.contains("\"$it\"", ignoreCase = true))
        }
    }
}
