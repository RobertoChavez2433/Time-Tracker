package com.robertochavez.timetracker.core.common.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class DriveMileageCalculatorTest {
    @Test
    fun `first usable point becomes baseline without adding distance`() {
        val point = point(longitude = -84.0000, at = Instant.parse("2026-05-05T12:00:00Z"))

        val result = DriveMileageCalculator.calculate(previous = null, current = point)

        assertEquals(point, result.baseline)
        assertEquals(0.0, result.distanceMeters, 0.001)
    }

    @Test
    fun `reasonable driving segment returns distance and advances baseline`() {
        val previous = point(longitude = -84.0000, at = Instant.parse("2026-05-05T12:00:00Z"))
        val current = point(longitude = -84.0010, at = Instant.parse("2026-05-05T12:00:30Z"))

        val result = DriveMileageCalculator.calculate(previous, current)

        assertEquals(current, result.baseline)
        assertEquals(93.0, result.distanceMeters, 5.0)
    }

    @Test
    fun `bad accuracy is ignored without moving baseline`() {
        val previous = point(longitude = -84.0000, at = Instant.parse("2026-05-05T12:00:00Z"))
        val current = point(
            longitude = -84.0010,
            accuracyMeters = 150f,
            at = Instant.parse("2026-05-05T12:00:30Z"),
        )

        val result = DriveMileageCalculator.calculate(previous, current)

        assertSame(previous, result.baseline)
        assertEquals(0.0, result.distanceMeters, 0.001)
    }

    @Test
    fun `impossible jump resets baseline without adding distance`() {
        val previous = point(longitude = -84.0000, at = Instant.parse("2026-05-05T12:00:00Z"))
        val current = point(longitude = -84.0100, at = Instant.parse("2026-05-05T12:00:05Z"))

        val result = DriveMileageCalculator.calculate(previous, current)

        assertEquals(current, result.baseline)
        assertEquals(0.0, result.distanceMeters, 0.001)
    }

    private fun point(longitude: Double, accuracyMeters: Float = 10f, at: Instant): DriveMileagePoint = DriveMileagePoint(
        latitude = 33.0000,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        at = at,
    )
}
