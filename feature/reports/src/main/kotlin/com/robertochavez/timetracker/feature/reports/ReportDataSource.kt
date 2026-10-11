package com.robertochavez.timetracker.feature.reports

import com.robertochavez.timetracker.core.common.domain.ReportCalculator
import com.robertochavez.timetracker.core.common.model.DateRange
import com.robertochavez.timetracker.core.common.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.common.repository.WorkSiteSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

class ReportDataSource @Inject constructor(
    private val tracking: TrackingRepository,
    private val schedule: WorkScheduleRepository,
    private val payPeriod: PayPeriodSettingsRepository,
    private val worksites: WorkSiteSessionRepository,
    private val clock: Clock,
) {
    internal fun observe(range: DateRange): Flow<ReportInputs> {
        val start = range.startInclusive.atStartOfDay(clock.zone).toInstant()
        val end = range.endExclusive.atStartOfDay(clock.zone).toInstant()
        return combine(
            tracking.observeSessions(start, end),
            tracking.observeActivityIntervals(start, end),
            worksites.observeWorkSiteSessions(start, end),
            schedule.observeWorkSchedule(),
            payPeriod.observeSettings(),
        ) { sessions, intervals, visits, workSchedule, period ->
            ReportInputs(sessions, intervals, visits, workSchedule, period)
        }
    }
}

internal fun ReportInputs.calculate(range: DateRange, clock: Clock) =
    ReportCalculator(clock.zone, schedule, payPeriod).range(range, sessions, intervals, clock.instant(), workSiteSessions)

internal fun weekRange(date: LocalDate): DateRange {
    val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return DateRange(start, start.plusWeeks(1))
}
