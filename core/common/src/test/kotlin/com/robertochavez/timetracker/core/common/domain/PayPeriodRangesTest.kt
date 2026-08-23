package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.PayPeriodCadence
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PayPeriodRangesTest {
    @Test
    fun `weekly cadence uses seven days rather than hardcoded fourteen`() {
        val settings = PayPeriodSettings(
            anchorStartDate = LocalDate.of(2026, 7, 6),
            cadence = PayPeriodCadence.WEEKLY,
        )

        val range = settings.payPeriodRangeFor(LocalDate.of(2026, 7, 15))

        assertEquals(LocalDate.of(2026, 7, 13), range.startInclusive)
        assertEquals(LocalDate.of(2026, 7, 20), range.endExclusive)
    }

    @Test
    fun `anchor is inclusive first day rather than payday or period end`() {
        val settings = PayPeriodSettings(
            anchorStartDate = LocalDate.of(2026, 7, 6),
            cadence = PayPeriodCadence.BIWEEKLY,
        )

        val range = settings.payPeriodRangeFor(LocalDate.of(2026, 7, 6))

        assertEquals(LocalDate.of(2026, 7, 6), range.startInclusive)
        assertEquals(LocalDate.of(2026, 7, 20), range.endExclusive)
    }

    @Test
    fun `dates before anchor and across year boundary use floor modulo`() {
        val settings = PayPeriodSettings(
            anchorStartDate = LocalDate.of(2026, 1, 5),
            cadence = PayPeriodCadence.BIWEEKLY,
        )

        val range = settings.payPeriodRangeFor(LocalDate.of(2025, 12, 31))

        assertEquals(LocalDate.of(2025, 12, 22), range.startInclusive)
        assertEquals(LocalDate.of(2026, 1, 5), range.endExclusive)
    }
}
