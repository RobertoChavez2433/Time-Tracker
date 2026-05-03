package com.robertochavez.timetracker.core.common.model

import java.time.LocalDate

data class DateRange(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
) {
    init {
        require(endExclusive > startInclusive) { "Date range end must be after start." }
    }

    fun contains(date: LocalDate): Boolean = !date.isBefore(startInclusive) && date.isBefore(endExclusive)
}
