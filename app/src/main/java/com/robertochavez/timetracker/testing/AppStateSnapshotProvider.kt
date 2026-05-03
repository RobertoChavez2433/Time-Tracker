package com.robertochavez.timetracker.testing

import com.robertochavez.timetracker.core.common.repository.AppSettingsRepository
import com.robertochavez.timetracker.core.common.repository.HomeLocationRepository
import com.robertochavez.timetracker.core.common.repository.TrackingRepository
import com.robertochavez.timetracker.core.common.repository.WorkScheduleRepository
import com.robertochavez.timetracker.core.logging.AppLogger
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStateSnapshotProvider @Inject constructor(
    private val homeLocationRepository: HomeLocationRepository,
    private val trackingRepository: TrackingRepository,
    private val workScheduleRepository: WorkScheduleRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val logger: AppLogger,
    private val clock: Clock,
) {
    suspend fun build(runId: String, actorId: String): AppStateSnapshot {
        val home = homeLocationRepository.getHomeLocation()
        val sessions = trackingRepository.observeSessions().first()
        val intervals = trackingRepository.observeActivityIntervals().first()
        val schedule = workScheduleRepository.getWorkSchedule()
        val settings = appSettingsRepository.settings.first()
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val recentLogs = logger.recentEvents(limit = 50)

        return AppStateSnapshot(
            runId = runId,
            actorId = actorId,
            capturedAtEpochMillis = clock.millis(),
            homeSet = home != null,
            homeRadiusMeters = home?.radiusMeters,
            activeSession = sessions.firstOrNull { it.isActive }?.toSummary(),
            sessionCount = sessions.size,
            activityIntervalCount = intervals.size,
            trackableToday = schedule.isTrackable(today),
            workdayCount = schedule.trackableDays.size,
            privacyDisclosureAccepted = settings.privacyDisclosureAccepted,
            minimalActiveNotificationEnabled = settings.minimalActiveNotificationEnabled,
            liveTimerNotificationEnabled = settings.liveTimerNotificationEnabled,
            recentLogCount = recentLogs.size,
        )
    }
}
