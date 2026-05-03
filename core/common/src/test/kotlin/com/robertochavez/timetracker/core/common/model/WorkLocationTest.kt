package com.robertochavez.timetracker.core.common.model

import org.junit.Test
import java.time.Instant

class WorkLocationTest {
    @Test
    fun `allows five mile radius`() {
        WorkLocation(
            latitude = 35.0,
            longitude = -80.0,
            radiusMeters = WorkLocation.MAXIMUM_RADIUS_METERS,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects radius above five miles`() {
        WorkLocation(
            latitude = 35.0,
            longitude = -80.0,
            radiusMeters = WorkLocation.MAXIMUM_RADIUS_METERS + 1f,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
        )
    }
}
