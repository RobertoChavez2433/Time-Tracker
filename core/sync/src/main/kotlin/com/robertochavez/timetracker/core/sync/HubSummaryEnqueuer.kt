package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.common.domain.ProjectDaySummaryCalculator
import com.robertochavez.timetracker.core.common.domain.ReportCalculator
import com.robertochavez.timetracker.core.common.domain.toHubDailySummary
import com.robertochavez.timetracker.core.common.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.common.repository.WorkSiteSessionRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("LongParameterList")
class HubSummaryEnqueuer @Inject constructor(
    private val trackingRepository: TrackingRepository,
    private val workSiteSessionRepository: WorkSiteSessionRepository,
    private val workScheduleRepository: WorkScheduleRepository,
    private val payPeriodSettingsRepository: PayPeriodSettingsRepository,
    private val repository: HubSyncRepository,
    private val payloadBuilder: HubPayloadBuilder,
    private val settingsStore: HubSyncSettingsStore,
    private val clock: Clock,
) {
    suspend fun enqueueEligible() {
        val start = settingsStore.current().activationDate ?: return
        val today = LocalDate.now(clock)
        val now = clock.instant()
        val sessions = trackingRepository.observeSessions().first()
        val intervals = trackingRepository.observeActivityIntervals().first()
        val siteSessions = workSiteSessionRepository.observeWorkSiteSessions().first()
        val calculator = ReportCalculator(clock.zone, workScheduleRepository.getWorkSchedule(), payPeriodSettingsRepository.getSettings())
        generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.forEach { date ->
            val summary = calculator.daily(date, sessions, intervals, now, siteSessions).toHubDailySummary()
            repository.enqueue(summary.sourceId, HubPayloadType.DAILY_SUMMARY, payloadBuilder.daily(summary))
        }
        ProjectDaySummaryCalculator.calculate(siteSessions, clock.zone, now)
            .filter { !it.date.isBefore(start) }
            .forEach { summary -> repository.enqueue(summary.sourceId, HubPayloadType.PROJECT_DAY, payloadBuilder.projectDay(summary)) }
    }
}
