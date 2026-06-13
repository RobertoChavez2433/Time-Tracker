package com.robertochavez.timetracker.core.location.mileage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveMileageBaselineTest {
    @Test
    fun `fresh accurate last known location can seed baseline`() {
        assertTrue(
            isUsableDriveMileageBaseline(
                timeEpochMillis = NOW_MILLIS - FIVE_MINUTES_MILLIS,
                hasAccuracy = true,
                accuracyMeters = 25f,
                clockMillis = NOW_MILLIS,
            ),
        )
    }

    @Test
    fun `stale last known location cannot seed baseline`() {
        assertFalse(
            isUsableDriveMileageBaseline(
                timeEpochMillis = NOW_MILLIS - FIFTEEN_MINUTES_MILLIS,
                hasAccuracy = true,
                accuracyMeters = 25f,
                clockMillis = NOW_MILLIS,
            ),
        )
    }

    @Test
    fun `inaccurate last known location cannot seed baseline`() {
        assertFalse(
            isUsableDriveMileageBaseline(
                timeEpochMillis = NOW_MILLIS - FIVE_MINUTES_MILLIS,
                hasAccuracy = true,
                accuracyMeters = 150f,
                clockMillis = NOW_MILLIS,
            ),
        )
    }

    @Test
    fun `future last known location cannot seed baseline`() {
        assertFalse(
            isUsableDriveMileageBaseline(
                timeEpochMillis = NOW_MILLIS + 1_000L,
                hasAccuracy = true,
                accuracyMeters = 25f,
                clockMillis = NOW_MILLIS,
            ),
        )
    }

    private companion object {
        const val NOW_MILLIS = 1_800_000_000_000L
        const val FIVE_MINUTES_MILLIS = 5 * 60 * 1_000L
        const val FIFTEEN_MINUTES_MILLIS = 15 * 60 * 1_000L
    }
}
