package com.robertochavez.timetracker.core.common.model

import java.time.DayOfWeek
import java.time.LocalDate

data class WorkSchedule(
    val trackableDays: Set<DayOfWeek> = DEFAULT_TRACKABLE_DAYS,
) {
    fun isTrackable(date: LocalDate): Boolean = date.dayOfWeek in trackableDays

    companion object {
        val DEFAULT_TRACKABLE_DAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }
}
