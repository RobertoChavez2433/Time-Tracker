package com.robertochavez.timetracker.core.common.model

import org.junit.Test
import java.time.Instant

class WorkLocationTest {
    @Test
    fun `allows twenty five mile radius`() {
        WorkLocation(
            latitude = 35.0,
            longitude = -80.0,
            radiusMeters = 25f * 1609.344f,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
        )
    }

    @Test
    fun `allows maximum radius`() {
        WorkLocation(
            latitude = 35.0,
            longitude = -80.0,
            radiusMeters = WorkLocation.MAXIMUM_RADIUS_METERS,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects radius above twenty five miles`() {
        WorkLocation(
            latitude = 35.0,
            longitude = -80.0,
            radiusMeters = 25f * 1609.344f + 1f,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects fractional mile radius`() {
        WorkLocation(
            latitude = 35.0,
            longitude = -80.0,
            radiusMeters = 1.5f * GeofenceRadiusOptions.ONE_MILE_METERS,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects blank address metadata`() {
        WorkLocation(
            latitude = 35.0,
            longitude = -80.0,
            radiusMeters = WorkLocation.MAXIMUM_RADIUS_METERS,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
            address = " ",
        )
    }
}
