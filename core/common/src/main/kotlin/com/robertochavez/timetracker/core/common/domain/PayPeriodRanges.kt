package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.DateRange
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun PayPeriodSettings.payPeriodRangeFor(dateInPeriod: LocalDate): DateRange {
    val daysSinceAnchor = Math.floorMod(
        ChronoUnit.DAYS.between(anchorStartDate, dateInPeriod).toInt(),
        cadence.days,
    )
    val start = dateInPeriod.minusDays(daysSinceAnchor.toLong())
    return DateRange(start, start.plusDays(cadence.days.toLong()))
}
