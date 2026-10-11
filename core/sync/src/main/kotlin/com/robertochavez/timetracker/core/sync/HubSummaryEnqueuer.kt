package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.common.domain.ProjectDaySummaryCalculator
import com.robertochavez.timetracker.core.common.domain.ReportCalculator
import com.robertochavez.timetracker.core.common.domain.toHubDailySummary
import com.robertochavez.timetracker.core.common.model.SummaryStatus
import com.robertochavez.timetracker.core.common.repository.PayPeriodSettingsRepository
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.common.repository.WorkSiteSessionRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.ZoneId
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
        val now = clock.instant()
        val today = now.atZone(BUSINESS_ZONE).toLocalDate()
        val sessions = trackingRepository.observeSessions().first()
        val intervals = trackingRepository.observeActivityIntervals().first()
        val siteSessions = workSiteSessionRepository.observeWorkSiteSessions().first()
        val calculator =
            ReportCalculator(BUSINESS_ZONE, workScheduleRepository.getWorkSchedule(), payPeriodSettingsRepository.getSettings())
        generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.forEach { date ->
            val summary = calculator.daily(date, sessions, intervals, now, siteSessions).toHubDailySummary()
            val status = when {
                date == today -> SummaryStatus.PROVISIONAL
                else -> SummaryStatus.FINAL
            }
            repository.enqueue(summary.sourceId, HubPayloadType.DAILY_SUMMARY, payloadBuilder.daily(summary, status))
        }
        ProjectDaySummaryCalculator.calculate(siteSessions, BUSINESS_ZONE, now)
            .filter { !it.date.isBefore(start) }
            .forEach { summary -> repository.enqueue(summary.sourceId, HubPayloadType.PROJECT_DAY, payloadBuilder.projectDay(summary)) }
    }

    private companion object {
        val BUSINESS_ZONE: ZoneId = ZoneId.of("America/New_York")
    }
}
