package com.robertochavez.timetracker.testing

import com.robertochavez.timetracker.core.common.domain.ReportCalculator
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.common.model.DailyReport
import com.robertochavez.timetracker.core.common.model.PeriodReport
import com.robertochavez.timetracker.core.common.model.WorkSiteSession
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.logging.AppLogger
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStateSnapshotProvider @Inject constructor(
    private val setupSources: SetupSnapshotSources,
    private val trackingRepository: TrackingRepository,
    private val logger: AppLogger,
    private val clock: Clock,
) {
    suspend fun build(runId: String, actorId: String): AppStateSnapshot {
        val home = setupSources.homeLocationRepository.getHomeLocation()
        val workLocations = setupSources.workLocationRepository.getWorkLocations()
        val work = workLocations.firstOrNull()
        val workPresence = setupSources.workPresenceRepository.getWorkPresence()
        val workSiteSessions = setupSources.workSiteSessionRepository.observeWorkSiteSessions().first()
        val sessions = trackingRepository.observeSessions().first()
        val intervals = trackingRepository.observeActivityIntervals().first()
        val schedule = setupSources.workScheduleRepository.getWorkSchedule()
        val payPeriod = setupSources.payPeriodSettingsRepository.observeSettings().first()
        val settings = setupSources.appSettingsRepository.settings.first()
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val now = Instant.now(clock)
        val recentLogs = logger.recentEvents(limit = 50)
        val latestSession = sessions.firstOrNull()
        val reportCalculator = ReportCalculator(clock.zone, schedule, payPeriod)

        return AppStateSnapshot(
            runId = runId,
            actorId = actorId,
            capturedAtEpochMillis = clock.millis(),
            homeSet = home != null,
            homeLatitude = home?.latitude,
            homeLongitude = home?.longitude,
            homeRadiusMeters = home?.radiusMeters,
            workSet = work != null,
            workLocationCount = workLocations.size,
            workLocations = workLocations.map {
                mapOf(
                    "id" to it.id,
                    "label" to it.label,
                    "latitude" to it.latitude,
                    "longitude" to it.longitude,
                    "radiusMeters" to it.radiusMeters,
                )
            },
            workLatitude = work?.latitude,
            workLongitude = work?.longitude,
            workRadiusMeters = work?.radiusMeters,
            atWork = workPresence.atWork,
            workSiteSessionCount = workSiteSessions.size,
            activeSession = sessions.firstOrNull { it.isActive }?.toSummary(),
            latestSession = latestSession?.toSummary(),
            sessionCount = sessions.size,
            countedSessionCount = sessions.count { it.countsTowardTotals },
            manuallyAdjustedSessionCount = sessions.count { it.manuallyAdjusted },
            totalDrivenMiles = sessions.sumOf { it.drivenMiles },
            activityIntervalCount = intervals.size,
            trackableToday = schedule.isTrackable(today),
            todayDayOfWeek = today.dayOfWeek.name,
            workdayCount = schedule.trackableDays.size,
            workdays = DayOfWeek.entries.associate { day -> day.name to (day in schedule.trackableDays) },
            payPeriodAnchorDate = payPeriod.biweeklyAnchorStartDate.toString(),
            privacyDisclosureAccepted = settings.privacyDisclosureAccepted,
            minimalActiveNotificationEnabled = settings.minimalActiveNotificationEnabled,
            liveTimerNotificationEnabled = settings.liveTimerNotificationEnabled,
            reportTotals = reportSnapshots(reportCalculator, today, sessions, intervals, workSiteSessions, now),
            recentLogCount = recentLogs.size,
        )
    }

    private fun reportSnapshots(
        calculator: ReportCalculator,
        today: LocalDate,
        sessions: List<AwaySession>,
        intervals: List<ActivityInterval>,
        workSiteSessions: List<WorkSiteSession>,
        now: Instant,
    ): Map<String, ReportSnapshot> = mapOf(
        "today" to calculator.daily(today, sessions, intervals, now, workSiteSessions).toSnapshot(),
        "weekly" to calculator.weekly(today, sessions, intervals, now, workSiteSessions).toSnapshot(),
        "biweekly" to calculator.biweekly(today, sessions, intervals, now, workSiteSessions).toSnapshot(),
        "monthly" to calculator.monthly(YearMonth.from(today), sessions, intervals, now, workSiteSessions).toSnapshot(),
        "yearly" to calculator.yearly(today.year, sessions, intervals, now, workSiteSessions).toSnapshot(),
    )

    private fun DailyReport.toSnapshot(): ReportSnapshot = ReportSnapshot(
        awayMinutes = totalAway.toMinutes(),
        drivenMiles = drivenMiles,
        driveMinutes = drive.toMinutes(),
        siteMinutes = site.toMinutes(),
        idleMinutes = idle.toMinutes(),
        unclassifiedMinutes = unclassified.toMinutes(),
    )

    private fun PeriodReport.toSnapshot(): ReportSnapshot = ReportSnapshot(
        awayMinutes = totalAway.toMinutes(),
        drivenMiles = drivenMiles,
        driveMinutes = drive.toMinutes(),
        siteMinutes = site.toMinutes(),
        idleMinutes = idle.toMinutes(),
        unclassifiedMinutes = bucketTotals.getValue(ActivityBucket.UNCLASSIFIED).toMinutes(),
    )
}
